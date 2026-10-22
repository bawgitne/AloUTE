Add-Type -AssemblyName System.Drawing

function Resize-Image($srcPath, $dstPath, $width, $height) {
    $src = [System.Drawing.Image]::FromFile($srcPath)
    $dst = New-Object System.Drawing.Bitmap($width, $height)
    $g = [System.Drawing.Graphics]::FromImage($dst)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.DrawImage($src, 0, 0, $width, $height)
    $dst.Save($dstPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose()
    $dst.Dispose()
    $src.Dispose()
}

$logoSrc = (Resolve-Path '.\current\public\src\8e2eeef5-9036-4a62-921a-f8cf8047c69d.png').Path
$logoTextSrc = (Resolve-Path '.\current\public\src\832c52a9-5202-4c94-a22b-c414024854ef.png').Path

# 1. Update assets/images in aloute_mobile
Copy-Item $logoSrc '.\aloute_mobile\assets\images\logo.png' -Force
Copy-Item $logoTextSrc '.\aloute_mobile\assets\images\logo_text.png' -Force

# 2. Generate Android mipmap icons in aloute_mobile
$mipmaps = @{
    'mipmap-mdpi' = 48
    'mipmap-hdpi' = 72
    'mipmap-xhdpi' = 96
    'mipmap-xxhdpi' = 144
    'mipmap-xxxhdpi' = 192
}

foreach ($m in $mipmaps.Keys) {
    $size = $mipmaps[$m]
    $dir = Join-Path '.\aloute_mobile\android\app\src\main\res' $m
    if (Test-Path $dir) {
        $out = Join-Path $dir 'ic_launcher.png'
        Resize-Image $logoSrc $out $size $size
        Write-Output "Generated aloute_mobile $m ($size x $size)"
    }
}

# 3. Web icons
if (Test-Path '.\aloute_mobile\web\icons') {
    Resize-Image $logoSrc '.\aloute_mobile\web\icons\Icon-192.png' 192 192
    Resize-Image $logoSrc '.\aloute_mobile\web\icons\Icon-512.png' 512 512
    Resize-Image $logoSrc '.\aloute_mobile\web\favicon.png' 64 64
    Write-Output 'Generated aloute_mobile web icons'
}

# 4. Also sync everything into current\aloute_mobile
if (Test-Path '.\current\aloute_mobile') {
    if (-not (Test-Path '.\current\aloute_mobile\assets\images')) {
        New-Item -ItemType Directory -Path '.\current\aloute_mobile\assets\images' -Force | Out-Null
    }
    Copy-Item '.\aloute_mobile\assets\images\*' '.\current\aloute_mobile\assets\images\' -Force
    foreach ($m in $mipmaps.Keys) {
        $dir = Join-Path '.\current\aloute_mobile\android\app\src\main\res' $m
        if (Test-Path $dir) {
            $out = Join-Path $dir 'ic_launcher.png'
            $size = $mipmaps[$m]
            Resize-Image $logoSrc $out $size $size
            Write-Output "Generated current\aloute_mobile $m ($size x $size)"
        }
    }
}
Write-Output "SUCCESS: Logo and App Icon replacement complete."
