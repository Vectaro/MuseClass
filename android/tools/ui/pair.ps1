param($proto, $emu, $out)
Add-Type -AssemblyName System.Drawing
New-Item -ItemType Directory -Force $out | Out-Null
$H = 1000; $top = 44; $gap = 24
$enc = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq 'image/jpeg' }
$ep = New-Object System.Drawing.Imaging.EncoderParameters 1
$ep.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter ([System.Drawing.Imaging.Encoder]::Quality, [long]84)
$font = New-Object System.Drawing.Font 'Segoe UI', 18
$n = 0
Get-ChildItem $emu -Filter *.png | ForEach-Object {
  $name = $_.BaseName
  $p = Join-Path $proto ($name + '.png')
  $e = [System.Drawing.Image]::FromFile($_.FullName)
  $we = [int]($e.Width * $H / $e.Height)
  $wp = 0; $pi = $null
  if (Test-Path $p) { $pi = [System.Drawing.Image]::FromFile($p); $wp = [int]($pi.Width * $H / $pi.Height) }
  $W = $wp + $we + ($(if ($pi) { $gap } else { 0 }))
  $bmp = New-Object System.Drawing.Bitmap $W, ($H + $top)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $g.Clear([System.Drawing.Color]::FromArgb(255, 214, 210, 200))
  $x = 0
  if ($pi) { $g.DrawString('Прототип', $font, [System.Drawing.Brushes]::Black, 8, 6); $g.DrawImage($pi, 0, $top, $wp, $H); $x = $wp + $gap; $pi.Dispose() }
  $g.DrawString('Android', $font, [System.Drawing.Brushes]::Black, $x + 8, 6)
  $g.DrawImage($e, $x, $top, $we, $H)
  $bmp.Save((Join-Path $out ($name + '.jpg')), $enc, $ep)
  $g.Dispose(); $bmp.Dispose(); $e.Dispose(); $n++
}
"зібрано: $n"
