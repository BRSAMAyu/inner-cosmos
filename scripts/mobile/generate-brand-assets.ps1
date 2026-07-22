[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing
$root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path

function New-Canvas([int]$width, [int]$height, [bool]$transparent = $false) {
    $bitmap = [Drawing.Bitmap]::new($width, $height, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    if ($transparent) { $graphics.Clear([Drawing.Color]::Transparent) }
    else {
        $rect = [Drawing.Rectangle]::new(0, 0, $width, $height)
        $bg = [Drawing.Drawing2D.LinearGradientBrush]::new($rect, [Drawing.Color]::FromArgb(255,22,17,15), [Drawing.Color]::FromArgb(255,39,30,27), 135)
        $graphics.FillRectangle($bg, $rect); $bg.Dispose()
    }
    return @($bitmap, $graphics)
}

function Draw-Mark([Drawing.Graphics]$graphics, [float]$cx, [float]$cy, [float]$size, [bool]$withGlow = $true) {
    if ($withGlow) {
        for ($i = 8; $i -ge 1; $i--) {
            $alpha = [int](3 + (9 - $i) * 2)
            $glow = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb($alpha, 221, 190, 127))
            $radius = $size * (0.34 + $i * 0.025)
            $graphics.FillEllipse($glow, $cx-$radius, $cy-$radius, $radius*2, $radius*2); $glow.Dispose()
        }
    }
    $orbitGold = [Drawing.Pen]::new([Drawing.Color]::FromArgb(235,222,190,126), [Math]::Max(2,$size*0.035))
    $orbitGold.StartCap = $orbitGold.EndCap = [Drawing.Drawing2D.LineCap]::Round
    $orbitSage = [Drawing.Pen]::new([Drawing.Color]::FromArgb(220,164,188,169), [Math]::Max(2,$size*0.022))
    $orbitSage.StartCap = $orbitSage.EndCap = [Drawing.Drawing2D.LineCap]::Round
    $state = $graphics.Save(); $graphics.TranslateTransform($cx,$cy); $graphics.RotateTransform(-28)
    $graphics.DrawArc($orbitGold, -$size*0.39, -$size*0.23, $size*0.78, $size*0.46, 18, 292)
    $graphics.DrawArc($orbitSage, -$size*0.29, -$size*0.42, $size*0.58, $size*0.84, 120, 255)
    $graphics.Restore($state)
    $orbitGold.Dispose(); $orbitSage.Dispose()

    $coreRect = [Drawing.RectangleF]::new($cx-$size*0.155,$cy-$size*0.155,$size*0.31,$size*0.31)
    $core = [Drawing.Drawing2D.LinearGradientBrush]::new($coreRect,[Drawing.Color]::FromArgb(255,244,217,158),[Drawing.Color]::FromArgb(255,170,195,177),45)
    $graphics.FillEllipse($core,$coreRect); $core.Dispose()
    $inner = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(255,31,23,21))
    $graphics.FillEllipse($inner,$cx-$size*0.075,$cy-$size*0.075,$size*0.15,$size*0.15); $inner.Dispose()
    $star = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(255,250,230,183))
    $r = $size*0.035
    $points = [Drawing.PointF[]]@(
        [Drawing.PointF]::new($cx,$cy-$r*1.8), [Drawing.PointF]::new($cx+$r*0.45,$cy-$r*0.45),
        [Drawing.PointF]::new($cx+$r*1.8,$cy), [Drawing.PointF]::new($cx+$r*0.45,$cy+$r*0.45),
        [Drawing.PointF]::new($cx,$cy+$r*1.8), [Drawing.PointF]::new($cx-$r*0.45,$cy+$r*0.45),
        [Drawing.PointF]::new($cx-$r*1.8,$cy), [Drawing.PointF]::new($cx-$r*0.45,$cy-$r*0.45)
    )
    $graphics.FillPolygon($star,$points); $star.Dispose()
    foreach ($dot in @(@(-.36,-.29,.022),@(.34,-.21,.016),@(.26,.34,.02),@(-.28,.31,.012))) {
        $brush = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(220,246,225,177))
        $d=$size*$dot[2]; $graphics.FillEllipse($brush,$cx+$size*$dot[0]-$d,$cy+$size*$dot[1]-$d,$d*2,$d*2); $brush.Dispose()
    }
}

function Save-Icon([string]$path, [int]$size, [bool]$transparent = $false, [float]$markScale = .82) {
    $canvas = New-Canvas $size $size $transparent; $bitmap=$canvas[0]; $graphics=$canvas[1]
    Draw-Mark $graphics ($size/2) ($size/2) ($size*$markScale) (-not $transparent)
    $dir=Split-Path $path -Parent; [IO.Directory]::CreateDirectory($dir) | Out-Null
    $bitmap.Save($path,[Drawing.Imaging.ImageFormat]::Png); $graphics.Dispose(); $bitmap.Dispose()
}

function Save-Splash([string]$path, [int]$width, [int]$height) {
    $canvas=New-Canvas $width $height; $bitmap=$canvas[0]; $graphics=$canvas[1]
    $scale=[Math]::Min($width,$height)
    Draw-Mark $graphics ($width/2) ($height/2-$scale*.035) ($scale*.38) $true
    $fontSize=[Math]::Max(12,$scale*.035)
    $font=[Drawing.Font]::new("Segoe UI",$fontSize,[Drawing.FontStyle]::Regular,[Drawing.GraphicsUnit]::Pixel)
    $brush=[Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(210,224,208,182))
    $format=[Drawing.StringFormat]::new(); $format.Alignment=[Drawing.StringAlignment]::Center
    $graphics.DrawString("I N N E R   C O S M O S",$font,$brush,[Drawing.RectangleF]::new(0,$height/2+$scale*.19,$width,$fontSize*1.6),$format)
    $dir=Split-Path $path -Parent; [IO.Directory]::CreateDirectory($dir) | Out-Null
    $bitmap.Save($path,[Drawing.Imaging.ImageFormat]::Png)
    $format.Dispose(); $brush.Dispose(); $font.Dispose(); $graphics.Dispose(); $bitmap.Dispose()
}

$master=Join-Path $root "web\brand\inner-cosmos-icon-1024.png"
Save-Icon $master 1024 $false .78
Save-Icon (Join-Path $root "web\public\icons\icon-512.png") 512
Save-Icon (Join-Path $root "web\public\icons\icon-512-maskable.png") 512 $false .62
Save-Icon (Join-Path $root "web\public\icons\icon-192.png") 192

$density=@{mdpi=48;hdpi=72;xhdpi=96;xxhdpi=144;xxxhdpi=192}
foreach($name in $density.Keys) {
    $dir=Join-Path $root "web\android\app\src\main\res\mipmap-$name"
    Save-Icon (Join-Path $dir "ic_launcher.png") $density[$name]
    Save-Icon (Join-Path $dir "ic_launcher_round.png") $density[$name]
    Save-Icon (Join-Path $dir "ic_launcher_foreground.png") ([int]($density[$name]*2.25)) $true .55
}

$splashes=@{
 "drawable\splash.png"=@(480,320); "drawable-land-mdpi\splash.png"=@(480,320); "drawable-land-hdpi\splash.png"=@(800,480);
 "drawable-land-xhdpi\splash.png"=@(1280,720); "drawable-land-xxhdpi\splash.png"=@(1600,960); "drawable-land-xxxhdpi\splash.png"=@(1920,1280);
 "drawable-port-mdpi\splash.png"=@(320,480); "drawable-port-hdpi\splash.png"=@(480,800); "drawable-port-xhdpi\splash.png"=@(720,1280);
 "drawable-port-xxhdpi\splash.png"=@(960,1600); "drawable-port-xxxhdpi\splash.png"=@(1280,1920)
}
foreach($entry in $splashes.GetEnumerator()) { Save-Splash (Join-Path $root ("web\android\app\src\main\res\"+$entry.Key)) $entry.Value[0] $entry.Value[1] }

# Capacitor iOS accepts a 1024px marketing icon and 2732px launch artwork as its source assets.
Save-Icon (Join-Path $root "web\ios\App\App\Assets.xcassets\AppIcon.appiconset\AppIcon-512@2x.png") 1024
foreach($name in @("splash-2732x2732.png","splash-2732x2732-1.png","splash-2732x2732-2.png")) {
    Save-Splash (Join-Path $root ("web\ios\App\App\Assets.xcassets\Splash.imageset\"+$name)) 2732 2732
}
Write-Output "BRAND_MASTER=$master"
