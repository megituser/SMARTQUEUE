$body = @{
    email = "admin@smartqueue.com"
    password = "admin123"
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/login" -Method Post -Body $body -ContentType "application/json"
    $response | ConvertTo-Json -Depth 5
} catch {
    Write-Host "Status: $($_.Exception.Response.StatusCode)"
    $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    $reader.BaseStream.Position = 0
    $reader.ReadToEnd()
}
