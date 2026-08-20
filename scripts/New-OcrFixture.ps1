function New-OcrFixture([string]$Caminho) {
    Add-Type -AssemblyName System.Drawing

    $caminhoImagem = [System.IO.Path]::ChangeExtension($Caminho, ".jpg")
    $imagem = [System.Drawing.Bitmap]::new(1400, 700)
    $grafico = [System.Drawing.Graphics]::FromImage($imagem)
    $fonteTitulo = [System.Drawing.Font]::new("Arial", 42, [System.Drawing.FontStyle]::Bold)
    $fonteTexto = [System.Drawing.Font]::new("Arial", 34, [System.Drawing.FontStyle]::Regular)

    try {
        $grafico.Clear([System.Drawing.Color]::White)
        $grafico.DrawString("Politica digitalizada de reembolso", $fonteTitulo,
            [System.Drawing.Brushes]::Black, 70, 150)
        $grafico.DrawString("O prazo especial para reembolso digitalizado e de 45 dias.", $fonteTexto,
            [System.Drawing.Brushes]::Black, 70, 270)
        $grafico.DrawString("Informe o numero do pedido ao atendimento.", $fonteTexto,
            [System.Drawing.Brushes]::Black, 70, 350)
        $imagem.Save($caminhoImagem, [System.Drawing.Imaging.ImageFormat]::Jpeg)
    } finally {
        $fonteTexto.Dispose()
        $fonteTitulo.Dispose()
        $grafico.Dispose()
        $imagem.Dispose()
    }

    $dadosImagem = [System.IO.File]::ReadAllBytes($caminhoImagem)
    $memoria = [System.IO.MemoryStream]::new()
    $escritor = [System.IO.BinaryWriter]::new($memoria, [System.Text.Encoding]::ASCII, $true)
    $posicoes = [System.Collections.Generic.List[long]]::new()

    try {
        function Write-Ascii([System.IO.BinaryWriter]$Destino, [string]$Texto) {
            $Destino.Write([System.Text.Encoding]::ASCII.GetBytes($Texto))
        }

        Write-Ascii $escritor "%PDF-1.4`n"

        $posicoes.Add($memoria.Position)
        Write-Ascii $escritor "1 0 obj`n<< /Type /Catalog /Pages 2 0 R >>`nendobj`n"

        $posicoes.Add($memoria.Position)
        Write-Ascii $escritor "2 0 obj`n<< /Type /Pages /Kids [3 0 R] /Count 1 >>`nendobj`n"

        $posicoes.Add($memoria.Position)
        Write-Ascii $escritor "3 0 obj`n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 700 350] /Resources << /XObject << /Imagem 4 0 R >> >> /Contents 5 0 R >>`nendobj`n"

        $posicoes.Add($memoria.Position)
        Write-Ascii $escritor "4 0 obj`n<< /Type /XObject /Subtype /Image /Width 1400 /Height 700 /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length $($dadosImagem.Length) >>`nstream`n"
        $escritor.Write($dadosImagem)
        Write-Ascii $escritor "`nendstream`nendobj`n"

        $conteudoPagina = "q`n700 0 0 350 0 0 cm`n/Imagem Do`nQ`n"
        $posicoes.Add($memoria.Position)
        Write-Ascii $escritor "5 0 obj`n<< /Length $([System.Text.Encoding]::ASCII.GetByteCount($conteudoPagina)) >>`nstream`n$conteudoPagina"
        Write-Ascii $escritor "endstream`nendobj`n"

        $inicioReferencias = $memoria.Position
        Write-Ascii $escritor "xref`n0 6`n0000000000 65535 f `n"
        foreach ($posicao in $posicoes) {
            Write-Ascii $escritor ("{0:D10} 00000 n `n" -f $posicao)
        }
        Write-Ascii $escritor "trailer`n<< /Size 6 /Root 1 0 R >>`nstartxref`n$inicioReferencias`n%%EOF`n"
        $escritor.Flush()
        [System.IO.File]::WriteAllBytes($Caminho, $memoria.ToArray())
    } finally {
        $escritor.Dispose()
        $memoria.Dispose()
        Remove-Item -LiteralPath $caminhoImagem -Force -ErrorAction SilentlyContinue
    }
}

function New-ImageFixture([string]$Caminho) {
    Add-Type -AssemblyName System.Drawing

    $imagem = [System.Drawing.Bitmap]::new(1400, 700)
    $grafico = [System.Drawing.Graphics]::FromImage($imagem)
    $fonteTitulo = [System.Drawing.Font]::new("Arial", 42, [System.Drawing.FontStyle]::Bold)
    $fonteTexto = [System.Drawing.Font]::new("Arial", 34, [System.Drawing.FontStyle]::Regular)
    try {
        $grafico.Clear([System.Drawing.Color]::White)
        $grafico.DrawString("Comprovante visual do pedido", $fonteTitulo,
            [System.Drawing.Brushes]::Black, 70, 150)
        $grafico.DrawString("Pedido 8742 aprovado para processamento.", $fonteTexto,
            [System.Drawing.Brushes]::Black, 70, 270)
        $imagem.Save($Caminho, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $fonteTexto.Dispose()
        $fonteTitulo.Dispose()
        $grafico.Dispose()
        $imagem.Dispose()
    }
}
