Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$outDir = "c:\Users\LENOVO\OneDrive\Videos\aloute\current\docs\report-assets\mobile-runtime-ui"
if (-not (Test-Path $outDir)) {
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
}

function Capture-Screen([string]$name) {
    adb shell screencap -p "/sdcard/$name.png"
    adb pull "/sdcard/$name.png" "$outDir\$name.png"
    Write-Host "[CAPTURED] $name.png"
}

Write-Host ">>> Starting automated capture of all mobile screens in TRUE White Balance..."

# 1. Ensure Feed active and at top
adb shell input tap 72 1550
Start-Sleep -Milliseconds 800
Capture-Screen "03-home-feed"

# 2. Scroll feed & Post detail
adb shell input swipe 360 1200 360 400 300
Start-Sleep -Milliseconds 800
Capture-Screen "03-home-feed-b"

adb shell input tap 360 300
Start-Sleep -Milliseconds 1200
Capture-Screen "06-post-detail"

# Back to feed via in-app back button
adb shell input tap 65 60
Start-Sleep -Milliseconds 600
adb shell input swipe 360 400 360 1200 300
Start-Sleep -Milliseconds 600

# 3. Create Post dialog (modal & typed)
adb shell input tap 360 1530
Start-Sleep -Milliseconds 1200
Capture-Screen "05-create-post-a"
adb shell input tap 360 400
Start-Sleep -Milliseconds 400
adb shell input text "Chuc_mung_nam_hoc_moi_2026_AloUTE"
Start-Sleep -Milliseconds 800
Capture-Screen "05-create-post-b"
adb shell input tap 65 60
Start-Sleep -Milliseconds 600
adb shell input tap 550 880
Start-Sleep -Milliseconds 600

# 4. Search & Community & Friends
adb shell input tap 216 1550
Start-Sleep -Milliseconds 1000
Capture-Screen "07-search-a"

adb shell input swipe 600 600 100 600 300
Start-Sleep -Milliseconds 1000
Capture-Screen "08-community-a"

adb shell input tap 360 300
Start-Sleep -Milliseconds 1400
Capture-Screen "08-community-detail"
adb shell input tap 710 290
Start-Sleep -Milliseconds 800
Capture-Screen "08-community-joined"
adb shell input tap 65 60
Start-Sleep -Milliseconds 600

adb shell input swipe 600 600 100 600 300
Start-Sleep -Milliseconds 1000
Capture-Screen "07-search-b"

adb shell input tap 360 940
Start-Sleep -Milliseconds 1200
Capture-Screen "14-friends"

adb shell input tap 550 200
Start-Sleep -Milliseconds 1000
Capture-Screen "14-friends-b"
adb shell input tap 65 60
Start-Sleep -Milliseconds 600

# Explore Search with keyboard
adb shell input tap 620 115
Start-Sleep -Milliseconds 800
Capture-Screen "08-community-b"
adb shell input tap 72 1550
Start-Sleep -Milliseconds 600

# 5. Notifications
adb shell input tap 504 1550
Start-Sleep -Milliseconds 1200
Capture-Screen "10-notifications-a"

adb shell input tap 550 115
Start-Sleep -Milliseconds 1000
Capture-Screen "10-notifications-b"

# 6. Profile & Edit & Settings
adb shell input tap 648 1550
Start-Sleep -Milliseconds 1200
Capture-Screen "04-profile-a"

adb shell input tap 620 580
Start-Sleep -Milliseconds 1200
Capture-Screen "04-edit-profile"
adb shell input tap 65 60
Start-Sleep -Milliseconds 800

adb shell input tap 580 470
Start-Sleep -Milliseconds 1200
Capture-Screen "12-settings"
adb shell input tap 65 60
Start-Sleep -Milliseconds 600

# 7. Messaging
adb shell input tap 72 1550
Start-Sleep -Milliseconds 800
adb shell input tap 670 80
Start-Sleep -Milliseconds 1200
Capture-Screen "09-messaging-a"

adb shell input tap 360 340
Start-Sleep -Milliseconds 1400
Capture-Screen "09-messaging-b"
adb shell input tap 65 60
Start-Sleep -Milliseconds 500
adb shell input tap 65 60
Start-Sleep -Milliseconds 600

# 8. Navigation Drawer & Admin
adb shell input tap 65 60
Start-Sleep -Milliseconds 1000
Capture-Screen "16-navigation-drawer"

# Tap Admin Dashboard (item 7: Y=870)
adb shell input tap 200 870
Start-Sleep -Milliseconds 1200
Capture-Screen "11-admin-dashboard"

# KPI Báo cáo chờ xử lý / Hàng đợi vi phạm (Y=980)
adb shell input tap 360 980
Start-Sleep -Milliseconds 1200
Capture-Screen "13-admin-reports"
adb shell input tap 65 60
Start-Sleep -Milliseconds 600

# Quản lý tài khoản sinh viên (Y=1130)
adb shell input tap 360 1130
Start-Sleep -Milliseconds 1200
Capture-Screen "12-admin-users"
adb shell input tap 65 60
Start-Sleep -Milliseconds 600

# Kiểm duyệt bài viết (Y=1280)
adb shell input tap 360 1280
Start-Sleep -Milliseconds 1200
Capture-Screen "17-admin-posts"
adb shell input tap 65 60
Start-Sleep -Milliseconds 600
adb shell input tap 65 60
Start-Sleep -Milliseconds 600

# 9. Saved posts
adb shell input tap 65 60
Start-Sleep -Milliseconds 800
adb shell input tap 200 480
Start-Sleep -Milliseconds 1200
Capture-Screen "15-saved"
adb shell input tap 65 60
Start-Sleep -Milliseconds 600

# 10. Logout & Auth
adb shell input tap 65 60
Start-Sleep -Milliseconds 800
adb shell input tap 200 1525
Start-Sleep -Milliseconds 1200
Capture-Screen "01-login"

adb shell input tap 550 880
Start-Sleep -Milliseconds 1200
Capture-Screen "00-forgot-password"
adb shell input tap 65 60
Start-Sleep -Milliseconds 600

adb shell input tap 480 1240
Start-Sleep -Milliseconds 1200
Capture-Screen "02-register"
adb shell input tap 65 60
Start-Sleep -Milliseconds 600

# 11. Log back in as student
adb shell input tap 360 1060
Start-Sleep -Milliseconds 1500

# Sync to root docs
$rootDest = "c:\Users\LENOVO\OneDrive\Videos\aloute\docs\report-assets\mobile-runtime-ui"
if (-not (Test-Path $rootDest)) { New-Item -ItemType Directory -Force -Path $rootDest | Out-Null }
Copy-Item -Path "$outDir\*" -Destination $rootDest -Recurse -Force

Write-Host ">>> ALL SCREENSHOTS SUCCESSFULLY RECAPTURED IN TRUE WHITE BALANCE & SYNCED!"
