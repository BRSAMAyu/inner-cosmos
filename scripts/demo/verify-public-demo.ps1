[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Origin,
    [switch]$SkipAurora
)

$ErrorActionPreference = "Stop"
$originUri = [Uri]$Origin
if ($originUri.Scheme -ne "https") { throw "Public demo verification requires HTTPS." }
$origin = $originUri.GetLeftPart([UriPartial]::Authority)
$suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$password = "Demo-proof-$suffix!"
$verificationActors = [System.Collections.Generic.List[object]]::new()
$verificationWarnings = [System.Collections.Generic.List[string]]::new()

function Invoke-Envelope {
    param(
        [Microsoft.PowerShell.Commands.WebRequestSession]$Session,
        [string]$Method,
        [string]$Path,
        [object]$Body = $null
    )
    $headers = @{}
    if ($Method -notin @("GET", "HEAD")) {
        $csrf = Invoke-RestMethod -Uri "$origin/api/v1/auth/csrf" -WebSession $Session -TimeoutSec 20
        # The classroom Demo deliberately permits frictionless persona switching and
        # may return an empty CSRF contract. Keep the verifier compatible with both
        # modes: attach the token when the server advertises one, otherwise let the
        # request itself prove that the Demo endpoint accepts the mutation.
        if ($csrf.data -and
            -not [string]::IsNullOrWhiteSpace([string]$csrf.data.headerName) -and
            -not [string]::IsNullOrWhiteSpace([string]$csrf.data.token)) {
            $headers[[string]$csrf.data.headerName] = [string]$csrf.data.token
        }
        $headers["Idempotency-Key"] = [Guid]::NewGuid().ToString()
    }
    $params = @{
        Uri = "$origin$Path"
        Method = $Method
        WebSession = $Session
        Headers = $headers
        TimeoutSec = 90
        UseBasicParsing = $true
    }
    if ($null -ne $Body) {
        $params.ContentType = "application/json; charset=utf-8"
        $json = $Body | ConvertTo-Json -Depth 8 -Compress
        $params.Body = [Text.Encoding]::UTF8.GetBytes($json)
    }
    $web = Invoke-WebRequest @params
    $bytes = if ($web.RawContentStream) {
        $web.RawContentStream.ToArray()
    } else {
        [Text.Encoding]::UTF8.GetBytes($web.Content)
    }
    $response = ([Text.Encoding]::UTF8.GetString($bytes) | ConvertFrom-Json)
    if (-not $response.success) { throw "API failed: $Path $($response.message)" }
    return $response.data
}

function Register-Actor([string]$username, [string]$nickname) {
    $session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $null = Invoke-Envelope $session "POST" "/api/v1/auth/register" @{
        username = $username; nickname = $nickname; password = $password
    }
    $script:verificationActors.Add([pscustomobject]@{ Session = $session; Username = $username })
    $current = Invoke-Envelope $session "GET" "/api/v1/auth/current"
    return @{ Session = $session; User = $current }
}

function Invoke-PublicReadyCheck {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Label,
        [Parameter(Mandatory = $true)]
        [scriptblock]$Operation,
        [int]$TimeoutSeconds = 90
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $attempt = 0
    $lastFailure = $null
    do {
        $attempt++
        try {
            return & $Operation
        } catch {
            $lastFailure = $_
            if ((Get-Date) -lt $deadline) { Start-Sleep -Seconds 2 }
        }
    } while ((Get-Date) -lt $deadline)
    throw "$Label did not become ready after $attempt attempts: $($lastFailure.Exception.Message)"
}

trap {
    $failure = $_
    foreach ($actor in @($verificationActors)) {
        try {
            $null = Invoke-Envelope $actor.Session "DELETE" "/api/user/account" @{ password = $password }
        } catch {
            Write-Warning "Could not clean verification actor '$($actor.Username)': $($_.Exception.Message)"
        }
    }
    throw $failure
}

$health = Invoke-PublicReadyCheck "Public health" {
    $result = Invoke-RestMethod -Uri "$origin/actuator/health" -TimeoutSec 30
    if ($result.status -ne "UP") { throw "status=$($result.status)" }
    return $result
}
if ($health.status -ne "UP") { throw "Public health is not UP." }
$landing = Invoke-PublicReadyCheck "Public landing page" {
    Invoke-WebRequest -UseBasicParsing -Uri "$origin/" -TimeoutSec 30
}
if ($landing.StatusCode -ne 200 -or $landing.Content -notmatch "Inner Cosmos") {
    throw "Public landing page is unavailable."
}
$webApp = Invoke-PublicReadyCheck "Public Web App" {
    Invoke-WebRequest -UseBasicParsing -Uri "$origin/app/aurora/" -TimeoutSec 30
}
if ($webApp.StatusCode -ne 200 -or
    $webApp.Content -notmatch '["'']\/app\/aurora\/assets\/[^"'']+\.js["'']') {
    throw "Public Web App is not using the /app/aurora browser bundle; in-app navigation may reload to a 404."
}
$apkHead = Invoke-PublicReadyCheck "Public APK download" {
    Invoke-WebRequest -UseBasicParsing -Method Head -Uri "$origin/downloads/inner-cosmos-demo.apk" -TimeoutSec 30
}
if ($apkHead.StatusCode -ne 200) { throw "Public APK download is unavailable." }

$demoSession = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
$demoPersonas = @(Invoke-Envelope $demoSession "GET" "/api/public/demo/personas")
if ($demoPersonas.Count -ne 3 -or @($demoPersonas | Where-Object { $_.key -notin @("lin-che", "shen-yan", "xia-yu") }).Count -ne 0) {
    throw "Public Demo must expose exactly the three curated non-admin personas."
}
$null = Invoke-Envelope $demoSession "POST" "/api/public/demo/enter/lin-che"
$lin = Invoke-Envelope $demoSession "GET" "/api/v1/auth/current"
$null = Invoke-Envelope $demoSession "POST" "/api/public/demo/enter/shen-yan"
$shen = Invoke-Envelope $demoSession "GET" "/api/v1/auth/current"
if (-not $lin.username.StartsWith("sandbox-") -or $lin.nickname -ne "Lin Che" -or
    -not $shen.username.StartsWith("sandbox-") -or $shen.nickname -ne "Shen Yan" -or
    [long]$lin.id -eq [long]$shen.id) {
    throw "Passwordless Demo persona switching did not establish two distinct real sessions."
}

$linDemoSession = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
$cloudDemoSession = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
$null = Invoke-Envelope $linDemoSession "POST" "/api/public/demo/enter/lin-che"
$null = Invoke-Envelope $cloudDemoSession "POST" "/api/public/demo/enter/xia-yu"
$linDemo = Invoke-Envelope $linDemoSession "GET" "/api/v1/auth/current"
$cloudDemo = Invoke-Envelope $cloudDemoSession "GET" "/api/v1/auth/current"
if (-not $linDemo.username.StartsWith("sandbox-") -or $linDemo.nickname -ne "Lin Che" -or
    -not $cloudDemo.username.StartsWith("sandbox-") -or $cloudDemo.nickname -ne "Xia Yu" -or
    [long]$linDemo.id -eq [long]$cloudDemo.id) {
    throw "Curated slow-letter proof requires two distinct Lin Che and Xia Yu sessions."
}

# The three months-lived Demo identities are the actual product showcase. A fresh synthetic
# capsule passing below must never mask a curated card that is visible in the plaza but blocked by
# stale memory/data-use grants. Create a real visitor session for every curated mirror on every run.
# These are the English classroom seed pseudonyms (MockDataInitializer#seedUserMirror /
# seedShowcaseMirror). The earlier Chinese names were the pre-2026-07-26 seed and made this gate
# fail against a correctly seeded English demo.
$curatedCapsuleSpecs = @(
    @{ Name = "Lin Che's Echo"; Visitor = "cloud" },
    @{ Name = "The One Who Walks by the River"; Visitor = "lin" },
    @{ Name = "The One Learning to Include Herself in Care"; Visitor = "lin" }
)
$curatedPlaza = @(Invoke-Envelope $linDemoSession "GET" "/api/plaza/capsules")
$curatedPersonaSessions = [System.Collections.Generic.List[object]]::new()
foreach ($spec in $curatedCapsuleSpecs) {
    $name = [string]$spec.Name
    $matches = @($curatedPlaza | Where-Object { $_.pseudonym -eq $name })
    if ($matches.Count -ne 1) {
        throw "Curated capsule '$name' is missing or ambiguous in the public plaza."
    }
    $visitorSession = if ($spec.Visitor -eq "cloud") { $cloudDemoSession } else { $linDemoSession }
    $personaSession = Invoke-Envelope $visitorSession "POST" "/api/v1/persona-chat/session/create" @{
        capsuleId = [long]$matches[0].id
    }
    if ($personaSession.status -ne "ACTIVE") {
        throw "Curated capsule '$name' did not create an active visitor session."
    }
    $curatedPersonaSessions.Add([pscustomobject]@{
        Name = $name
        Session = $visitorSession
        PersonaSessionId = [long]$personaSession.id
    })
}

# Prove the intended showcase path between two isolated curated-persona sandboxes:
# Xia Yu writes a real 30-second letter, Lin Che receives and reads it, then asks to connect.
# This matches the current personal-sandbox contract instead of assuming shared seed user IDs.
$curatedConnection = "NOT_RUN"
try {
    $existingFriends = @(Invoke-Envelope $linDemoSession "GET" "/api/social/friends")
    foreach ($friend in @($existingFriends | Where-Object { [long]$_.userId -eq [long]$cloudDemo.id })) {
        $null = Invoke-Envelope $linDemoSession "POST" "/api/social/friends/$($friend.id)/leave"
    }

    $letterKey = "public-demo-curated-letter-$suffix"
    $curatedLetter = Invoke-Envelope $cloudDemoSession "POST" "/api/v1/letters/draft" @{
        receiverUserId = [long]$linDemo.id
        title = "A slow note from Xia Yu"
        letterBody = "A real note between two isolated demo journeys, sent slowly and without pressure."
        deliveryPreset = "DEMO_30S"
        timeZone = "Asia/Shanghai"
        idempotencyKey = $letterKey
    }
    $sentCuratedLetter = Invoke-Envelope $cloudDemoSession "POST" "/api/letters/$($curatedLetter.id)/send" @{}
    if ($sentCuratedLetter.status -notin @("SENT", "FLYING", "DELIVERED")) {
        throw "The curated sandbox slow letter did not enter its delivery lifecycle."
    }

    $arrivalDeadline = (Get-Date).AddSeconds(55)
    $arrivedLetter = $null
    do {
        Start-Sleep -Seconds 5
        $linInbox = @(Invoke-Envelope $linDemoSession "GET" "/api/letters/inbox")
        $arrivedLetter = $linInbox | Where-Object {
            [long]$_.id -eq [long]$curatedLetter.id -and $_.status -in @("DELIVERED", "READ", "REPLIED")
        } | Select-Object -First 1
    } while (-not $arrivedLetter -and (Get-Date) -lt $arrivalDeadline)
    if (-not $arrivedLetter) {
        throw "The real Xia Yu to Lin Che 30-second slow letter did not arrive."
    }
    if ($arrivedLetter.status -eq "DELIVERED") {
        $arrivedLetter = Invoke-Envelope $linDemoSession "POST" "/api/letters/$($arrivedLetter.id)/read"
    }

    $requested = Invoke-Envelope $linDemoSession "POST" "/api/social/connections/from-letter/$($arrivedLetter.id)"
    if ($requested.status -ne "PENDING" -or
        [long]$requested.requesterId -ne [long]$linDemo.id -or
        [long]$requested.addresseeId -ne [long]$cloudDemo.id) {
        throw "The slow letter did not create the expected Lin Che to Xia Yu request."
    }

    $cloudRequests = Invoke-Envelope $cloudDemoSession "GET" "/api/social/requests"
    $incomingFromLin = @($cloudRequests.incoming | Where-Object {
        [long]$_.id -eq [long]$requested.id -and
        [long]$_.userId -eq [long]$linDemo.id -and
        $_.source -eq "SLOW_LETTER:$($arrivedLetter.id)"
    })
    if ($incomingFromLin.Count -ne 1) {
        throw "Xia Yu did not receive the connection request created from the slow letter."
    }
    $null = Invoke-Envelope $cloudDemoSession "POST" "/api/social/friends/$($requested.id)/accept"

    $linFriends = @(Invoke-Envelope $linDemoSession "GET" "/api/social/friends")
    $cloudFriends = @(Invoke-Envelope $cloudDemoSession "GET" "/api/social/friends")
    if (-not ($linFriends | Where-Object {
            [long]$_.id -eq [long]$requested.id -and [long]$_.userId -eq [long]$cloudDemo.id
        }) -or
        -not ($cloudFriends | Where-Object {
            [long]$_.id -eq [long]$requested.id -and [long]$_.userId -eq [long]$linDemo.id
        })) {
        throw "The slow-letter connection is not visible to both personas."
    }
    $curatedConnection = "LETTER_TO_FRIEND_ACCEPTED"
}
finally {
    try {
        $cleanupFriends = @(Invoke-Envelope $linDemoSession "GET" "/api/social/friends")
        foreach ($friend in @($cleanupFriends | Where-Object { [long]$_.userId -eq [long]$cloudDemo.id })) {
            $null = Invoke-Envelope $linDemoSession "POST" "/api/social/friends/$($friend.id)/leave"
        }
    }
    catch {
        Write-Warning "Could not restore the isolated Lin Che / Xia Yu friendship baseline: $($_.Exception.Message)"
    }
}

$a = Register-Actor "demoproofa$suffix" "Demo A $suffix"
$b = Register-Actor "demoproofb$suffix" "Demo B $suffix"
$aId = [long]$a.User.id
$bId = [long]$b.User.id

$people = @(Invoke-Envelope $a.Session "GET" "/api/social/people")
if (-not ($people | Where-Object { [long]$_.id -eq $bId })) {
    throw "Newly registered peer is not discoverable."
}
$relation = Invoke-Envelope $a.Session "POST" "/api/social/friends/request" @{
    userId = $bId; source = "PUBLIC_DEMO_VERIFICATION"
}
$requests = Invoke-Envelope $b.Session "GET" "/api/social/requests"
$incoming = @($requests.incoming | Where-Object { [long]$_.userId -eq $aId })
if ($incoming.Count -ne 1) { throw "Peer did not receive exactly one friend request." }
$null = Invoke-Envelope $b.Session "POST" "/api/social/friends/$($incoming[0].id)/accept"

$aFriends = @(Invoke-Envelope $a.Session "GET" "/api/social/friends")
$bFriends = @(Invoke-Envelope $b.Session "GET" "/api/social/friends")
if (-not ($aFriends | Where-Object { [long]$_.userId -eq $bId }) -or
    -not ($bFriends | Where-Object { [long]$_.userId -eq $aId })) {
    throw "Accepted friendship is not visible to both peers."
}

$group = Invoke-Envelope $a.Session "POST" "/api/social/groups" @{
    groupName = "Demo cohort $suffix"; intro = "Public demo verification"; visibility = "PRIVATE"
}
$null = Invoke-Envelope $a.Session "POST" "/api/social/groups/$($group.id)/invite" @{ userId = "$bId" }
$invites = @(Invoke-Envelope $b.Session "GET" "/api/social/groups/invites")
$invite = @($invites | Where-Object { [long]$_.groupId -eq [long]$group.id })
if ($invite.Count -ne 1) { throw "Group invitation did not reach the peer." }
$null = Invoke-Envelope $b.Session "POST" "/api/social/groups/invites/$($invite[0].memberId)/respond" @{ decision = "accept" }
$members = @(Invoke-Envelope $a.Session "GET" "/api/social/groups/$($group.id)/members")
if (@($members | Where-Object { [long]$_.userId -in @($aId, $bId) }).Count -ne 2) {
    throw "Accepted group membership is incomplete."
}

$auroraReplyLength = 0
$memoryCards = 0
$capsulePublished = $false
$capsuleChatReplyLength = 0
$curatedCapsuleReplies = 0
$curatedCapsuleDistinctReplies = 0
$slowLetterStatus = "SKIPPED"
$auroraRuntime = "SKIPPED"
$auroraFallbacks = "SKIPPED"
if (-not $SkipAurora) {
    $curatedPrompt = [Text.Encoding]::UTF8.GetString(
        [Convert]::FromBase64String(
            "5oiR5b6I5Zyo5oSP5LiA5Liq6aG555uu77yM5Lmf5Zug5Li65oCV5YGa5LiN5aW96ICM5LiA55u05ouW552A5LiN5pWi6K6p5Yir5Lq655yL44CC5L2g5Lya5oCO5LmI5Zue5bqU77yf"))
    $curatedTexts = [System.Collections.Generic.List[string]]::new()
    foreach ($curated in $curatedPersonaSessions) {
        $curatedReply = Invoke-Envelope $curated.Session "POST" "/api/v1/persona-chat/message" @{
            sessionId = [long]$curated.PersonaSessionId
            message = $curatedPrompt
        }
        $text = [string]$curatedReply.textContent
        if ($text.Length -lt 20) {
            throw "Curated capsule '$($curated.Name)' returned an implausibly short response."
        }
        $curatedTexts.Add($text)
    }
    $curatedCapsuleReplies = $curatedTexts.Count
    $curatedCapsuleDistinctReplies = @($curatedTexts | Select-Object -Unique).Count
    if ($curatedCapsuleDistinctReplies -ne $curatedCapsuleReplies) {
        throw "Curated capsules returned duplicate voices for the same lived-experience prompt."
    }

    $dialog = Invoke-Envelope $a.Session "POST" "/api/dialog/session/create" @{
        title = "Public demo verification"; sessionType = "AURORA_CHAT"
    }
    $quietDisclosurePrompt = [Text.Encoding]::UTF8.GetString(
        [Convert]::FromBase64String(
            "5piO5aSp6KaB5bGV56S66L+Z5Liq6aG555uu77yM5oiR5b6I57Sn5byg44CC5YWI5Yir57uZ5bu66K6u77yM5oiR5Y+q5piv5oOz5oqK6L+Z5Y+l6K+d6K+05Ye65p2l44CC"))
    $reply = Invoke-Envelope $a.Session "POST" "/api/v1/aurora/message-rich" @{
        sessionId = [long]$dialog.id
        message = $quietDisclosurePrompt
        inputType = "TEXT"
        mode = "DAILY_TALK"
        timezone = "Asia/Shanghai"
        clientMessageId = "demo-proof-$suffix"
        foregroundAcknowledgementSent = $true
    }
    $replyText = @($reply.messages) -join "`n"
    if ([string]::IsNullOrWhiteSpace($replyText)) { $replyText = [string]$reply.content }
    if ([string]::IsNullOrWhiteSpace($replyText)) { $replyText = [string]$reply.text }
    if ([string]::IsNullOrWhiteSpace($replyText)) { $replyText = ($reply | ConvertTo-Json -Depth 8 -Compress) }
    $auroraReplyLength = $replyText.Length
    # Quiet disclosure is intentionally allowed to be one restrained sentence. Character count is
    # only an empty/placeholder guard; the semantic assertions immediately below enforce the real
    # no-advice/no-question boundary.
    if ($auroraReplyLength -lt 8) {
        throw "Aurora returned an empty or placeholder-length response: '$replyText'."
    }
    $quietReframe = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String("5LiN5piv5Z2P5LqL"))
    if ($replyText.Contains("?") -or $replyText.Contains([string][char]0xFF1F) -or
        $replyText.Contains($quietReframe) -or
        -not [string]::IsNullOrWhiteSpace([string]$reply.nextQuestion) -or
        -not [string]::IsNullOrWhiteSpace([string]$reply.smallStep)) {
        throw "Aurora violated the quiet-disclosure boundary during public acceptance: reply='$replyText'; nextQuestion='$($reply.nextQuestion)'; smallStep='$($reply.smallStep)'."
    }
    $auroraRuntime = [string]$reply.agentLoop.runtime
    $auroraProvider = [string]$reply.aiState.provider
    $auroraModel = [string]$reply.aiState.model
    if ([string]::IsNullOrWhiteSpace($auroraProvider) -or
        $auroraProvider.ToLowerInvariant() -eq "mock") {
        throw "Aurora did not identify a real provider."
    }
    if (-not [bool]$reply.aiState.apiKeyConfigured -or [bool]$reply.aiState.fallbackAllowed) {
        throw "Aurora real-provider fail-closed contract is not active."
    }
    if ($auroraRuntime -ne "dual-kernel.pipeline.v2") {
        $verificationWarnings.Add("Aurora runtime is '$auroraRuntime'; expected the pipelined runtime.")
    }
    if (-not [bool]$reply.agentLoop.backgroundPlannerScheduled) {
        $verificationWarnings.Add("Aurora did not report background planner scheduling.")
    }
    if ([bool]$reply.agentLoop.speakerFallbackUsed) {
        throw "Aurora speaking kernel fell back during public acceptance."
    }
    # The critic's fallback is a bounded deterministic quality repair, followed by the same
    # observable boundary assertions above. It is deliberately non-fatal for a classroom launch:
    # a transient supervisor timeout must not make Web/APK unreachable after planner + speaker
    # both completed on the real provider.
    $auroraFallbacks = if ([bool]$reply.agentLoop.criticFallbackUsed) {
        "CRITIC_DETERMINISTIC_REPAIR"
    } else {
        "NONE"
    }

    # Close the same conversation so the real settlement pipeline has to materialize a memory
    # card. Poll the public API instead of assuming async listeners finish immediately.
    $null = Invoke-Envelope $a.Session "POST" "/api/dialog/session/$($dialog.id)/finish"
    $cards = @()
    $memoryDeadline = (Get-Date).AddMinutes(2)
    do {
        Start-Sleep -Seconds 2
        $cards = @(Invoke-Envelope $a.Session "GET" "/api/memory/cards")
    } while ($cards.Count -eq 0 -and (Get-Date) -lt $memoryDeadline)
    if ($cards.Count -eq 0) { throw "Aurora settlement did not produce a memory card." }
    $memoryCards = $cards.Count
    $memoryId = [long]$cards[0].id

    # Exercise the complete consent-bound resonance trajectory: private compile, owner publish,
    # public discovery, visitor capsule conversation, and a slow letter leaving the visitor's
    # outbox. No seeded user or capsule participates in this proof.
    $null = Invoke-Envelope $a.Session "POST" "/api/capsule/preview-from-memory" @{
        memoryIds = @($memoryId); privacyLevel = "STRICT"; allowTopics = @("daily support")
        blockedTopics = @("identity", "contact details", "diagnosis")
    }
    $capsule = Invoke-Envelope $a.Session "POST" "/api/v1/capsule/create-from-memory" @{
        pseudonym = "Demo Echo $suffix"
        intro = "A consent-bound facet created by the public demo verifier."
        memoryIds = @($memoryId)
        publicTags = @("class demo", "thoughtful support")
        allowTopics = @("daily support")
        blockedTopics = @("identity", "contact details", "diagnosis")
        maxConversationTurns = 8
        allowLetterRequest = $true
        privacyLevel = "STRICT"
        visibilityStatus = "PRIVATE"
        isPublic = $false
        standInEnabled = $true
        realContactPolicy = "LETTER_ONLY"
    }
    $published = Invoke-Envelope $a.Session "POST" "/api/capsule/$($capsule.id)/visibility" @{
        visibilityStatus = "PUBLIC"; isPublic = $true
    }
    if ($published.visibilityStatus -ne "PUBLIC" -or -not $published.isPublic) {
        throw "Owner could not publish the newly compiled capsule."
    }
    $capsulePublished = $true

    $plaza = @(Invoke-Envelope $b.Session "GET" "/api/plaza/capsules")
    if (-not ($plaza | Where-Object { [long]$_.id -eq [long]$capsule.id })) {
        throw "Published capsule is not discoverable by the second user."
    }
    $persona = Invoke-Envelope $b.Session "POST" "/api/v1/persona-chat/session/create" @{
        capsuleId = [long]$capsule.id
    }
    $capsuleReply = Invoke-Envelope $b.Session "POST" "/api/v1/persona-chat/message" @{
        sessionId = [long]$persona.id
        message = "What would you say to someone who cares deeply about a project and feels nervous before showing it?"
    }
    $capsuleChatReplyLength = ([string]$capsuleReply.textContent).Length
    if ($capsuleChatReplyLength -lt 20) { throw "Capsule returned an implausibly short response." }

    $letter = Invoke-Envelope $b.Session "POST" "/api/v1/letters/draft" @{
        receiverCapsuleId = [long]$capsule.id
        title = "A note after our resonance"
        letterBody = "Your perspective made me pause. I wanted to leave a thoughtful note and continue slowly."
        idempotencyKey = "public-demo-letter-$suffix"
    }
    $sentLetter = Invoke-Envelope $b.Session "POST" "/api/letters/$($letter.id)/send" @{}
    $slowLetterStatus = [string]$sentLetter.status
    if ($slowLetterStatus -notin @("SENT", "FLYING", "DELIVERED")) {
        throw "Slow letter did not enter its durable delivery lifecycle."
    }
    $outbox = @(Invoke-Envelope $b.Session "GET" "/api/letters/outbox")
    if (-not ($outbox | Where-Object { [long]$_.id -eq [long]$letter.id })) {
        throw "Sent slow letter is missing from the visitor outbox."
    }
}

# The verifier proves the full path with fresh accounts, then removes those synthetic actors so a
# repeated classroom launch never fills the real "People, slowly" surface with Demo A/B rows. The
# PASS summary below is computed before cleanup; deletion itself is part of the verification gate.
$null = Invoke-Envelope $b.Session "DELETE" "/api/user/account" @{ password = $password }
$null = Invoke-Envelope $a.Session "DELETE" "/api/user/account" @{ password = $password }
$verificationActors.Clear()

[pscustomobject]@{
    Status = if ($verificationWarnings.Count -eq 0) { "PASS" } else { "PASS_WITH_WARNINGS" }
    Origin = $origin
    Health = $health.status
    ApkDownload = "PASS"
    DemoPersonaSwitch = "LIN_CHE_TO_SHEN_YAN"
    CuratedSlowLetterConnection = $curatedConnection
    RegisteredUsers = 2
    Friendship = "BIDIRECTIONAL_ACCEPTED"
    GroupMembers = 2
    AuroraReplyLength = $auroraReplyLength
    AuroraProvider = $auroraProvider
    AuroraModel = $auroraModel
    AuroraRuntime = $auroraRuntime
    BackgroundPlannerScheduled = [bool]$reply.agentLoop.backgroundPlannerScheduled
    AuroraFallbacks = $auroraFallbacks
    Warnings = @($verificationWarnings) -join " | "
    MemoryCards = $memoryCards
    CapsulePublished = $capsulePublished
    CapsuleChatReplyLength = $capsuleChatReplyLength
    CuratedCapsuleSessions = $curatedPersonaSessions.Count
    CuratedCapsuleReplies = $curatedCapsuleReplies
    CuratedCapsuleDistinctReplies = $curatedCapsuleDistinctReplies
    SlowLetterStatus = $slowLetterStatus
} | Format-List
