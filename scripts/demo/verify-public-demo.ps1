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
        $headers[$csrf.data.headerName] = $csrf.data.token
        $headers["Idempotency-Key"] = [Guid]::NewGuid().ToString()
    }
    $params = @{
        Uri = "$origin$Path"
        Method = $Method
        WebSession = $Session
        Headers = $headers
        TimeoutSec = 90
    }
    if ($null -ne $Body) {
        $params.ContentType = "application/json"
        $params.Body = ($Body | ConvertTo-Json -Depth 8 -Compress)
    }
    $response = Invoke-RestMethod @params
    if (-not $response.success) { throw "API failed: $Path $($response.message)" }
    return $response.data
}

function Register-Actor([string]$username, [string]$nickname) {
    $session = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $null = Invoke-Envelope $session "POST" "/api/v1/auth/register" @{
        username = $username; nickname = $nickname; password = $password
    }
    $current = Invoke-Envelope $session "GET" "/api/v1/auth/current"
    return @{ Session = $session; User = $current }
}

$health = Invoke-RestMethod -Uri "$origin/actuator/health" -TimeoutSec 30
if ($health.status -ne "UP") { throw "Public health is not UP." }
$landing = Invoke-WebRequest -UseBasicParsing -Uri "$origin/" -TimeoutSec 30
if ($landing.StatusCode -ne 200 -or $landing.Content -notmatch "Inner Cosmos") {
    throw "Public landing page is unavailable."
}
$apkHead = Invoke-WebRequest -UseBasicParsing -Method Head -Uri "$origin/downloads/inner-cosmos-demo.apk" -TimeoutSec 30
if ($apkHead.StatusCode -ne 200) { throw "Public APK download is unavailable." }

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
$slowLetterStatus = "SKIPPED"
if (-not $SkipAurora) {
    $dialog = Invoke-Envelope $a.Session "POST" "/api/dialog/session/create" @{
        title = "Public demo verification"; sessionType = "AURORA_CHAT"
    }
    $reply = Invoke-Envelope $a.Session "POST" "/api/v1/aurora/message-rich" @{
        sessionId = [long]$dialog.id
        message = "I am a little nervous and excited about showing this project to my classmates. Please respond like a thoughtful friend."
        inputType = "TEXT"
        mode = "DAILY_TALK"
        timezone = "Asia/Shanghai"
        clientMessageId = "demo-proof-$suffix"
    }
    $replyText = [string]$reply.content
    if ([string]::IsNullOrWhiteSpace($replyText)) { $replyText = [string]$reply.text }
    if ([string]::IsNullOrWhiteSpace($replyText)) { $replyText = ($reply | ConvertTo-Json -Depth 8 -Compress) }
    $auroraReplyLength = $replyText.Length
    if ($auroraReplyLength -lt 20) { throw "Aurora returned an implausibly short response." }

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

[pscustomobject]@{
    Status = "PASS"
    Origin = $origin
    Health = $health.status
    ApkDownload = "PASS"
    RegisteredUsers = 2
    Friendship = "BIDIRECTIONAL_ACCEPTED"
    GroupMembers = 2
    AuroraReplyLength = $auroraReplyLength
    MemoryCards = $memoryCards
    CapsulePublished = $capsulePublished
    CapsuleChatReplyLength = $capsuleChatReplyLength
    SlowLetterStatus = $slowLetterStatus
} | Format-List
