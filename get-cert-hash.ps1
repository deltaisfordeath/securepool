param(
    [Parameter(Mandatory=$true)]
    [string]$CertPath
)

$openSSLPath = "C:\Program Files\OpenSSL-Win64\bin\openssl.exe"

try {
    if (Test-Path $openSSLPath) {
        Write-Host "Using OpenSSL to generate certificate hash..." -ForegroundColor Green
        
        # Use OpenSSL to generate the hash
        $hash = & $openSSLPath x509 -in $CertPath -pubkey -noout | & $openSSLPath pkey -pubin -outform der | & $openSSLPath dgst -sha256 -binary | & $openSSLPath enc -base64
        
        if ($hash) {
            Write-Host "Certificate SHA-256 Hash: sha256:$hash" -ForegroundColor Green
            Write-Host ""
            Write-Host "Copy this into your CertificatePinning.kt file:" -ForegroundColor Yellow
            Write-Host ".add(`"10.0.2.2`", `"sha256:$hash`")" -ForegroundColor Cyan
            Write-Host ".add(`"localhost`", `"sha256:$hash`")" -ForegroundColor Cyan
        } else {
            throw "Failed to generate hash using OpenSSL"
        }
    } else {
        throw "OpenSSL not found at expected path"
    }
}
catch {
    Write-Host "Error generating certificate hash: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host ""
    Write-Host "Fallback: Using .NET crypto APIs..." -ForegroundColor Yellow
    
    try {
        # Fallback to .NET method
        $certPem = Get-Content $CertPath -Raw
        $cert = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new([System.Text.Encoding]::UTF8.GetBytes($certPem))
        
        # Get public key bytes in DER format
        $publicKeyBytes = $cert.GetPublicKey()
        
        # Calculate SHA-256 hash
        $sha256 = [System.Security.Cryptography.SHA256]::Create()
        $hashBytes = $sha256.ComputeHash($publicKeyBytes)
        
        # Convert to Base64
        $base64Hash = [Convert]::ToBase64String($hashBytes)
        
        Write-Host "Certificate SHA-256 Hash: sha256:$base64Hash" -ForegroundColor Green
        Write-Host ""
        Write-Host "Copy this into your CertificatePinning.kt file:" -ForegroundColor Yellow
        Write-Host ".add(`"10.0.2.2`", `"sha256:$base64Hash`")" -ForegroundColor Cyan
        Write-Host ".add(`"localhost`", `"sha256:$base64Hash`")" -ForegroundColor Cyan
    }
    catch {
        Write-Host "Fallback method also failed: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host ""
        Write-Host "Alternative: Run the app and check logcat for the certificate hash." -ForegroundColor Yellow
        Write-Host "The SecurePoolApplication will log the hash on app startup." -ForegroundColor Yellow
    }
}
