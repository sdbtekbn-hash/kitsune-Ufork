# Script to initialize all Git submodules
$submodules = @(
    @{path="native/src/external/lz4"; url="https://github.com/lz4/lz4.git"},
    @{path="native/src/external/xz"; url="https://github.com/xz-mirror/xz.git"},
    @{path="native/src/external/libcxx"; url="https://github.com/topjohnwu/libcxx.git"},
    @{path="native/src/external/zopfli"; url="https://github.com/google/zopfli.git"},
    @{path="native/src/external/lsplt"; url="https://github.com/LSPosed/LSPlt.git"},
    @{path="native/src/external/system_properties"; url="https://github.com/topjohnwu/system_properties.git"},
    @{path="native/src/external/crt0"; url="https://github.com/topjohnwu/crt0.git"}
)

foreach ($submodule in $submodules) {
    Write-Host "Initializing $($submodule.path)..."
    if (Test-Path $submodule.path) {
        $files = Get-ChildItem $submodule.path -Force
        if ($files.Count -eq 0) {
            Set-Location $submodule.path
            git clone $submodule.url .
            Set-Location $PSScriptRoot
        } else {
            Write-Host "  Directory $($submodule.path) is not empty, skipping..."
        }
    } else {
        Write-Host "  Directory $($submodule.path) does not exist, skipping..."
    }
}

Write-Host "Submodule initialization complete!"
