package com.github.overz.bdd;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Conteúdos sintéticos por formato para os cenários de ingestão: cada gerador produz
 * exatamente {@code size} bytes com as assinaturas que o Tika (detecção por conteúdo)
 * e o check estrutural (D3) esperam — PDFs com {@code %PDF}/{@code %%EOF}, JPEG com
 * SOI/EOI, PNG com assinatura/IEND etc.
 */
public final class SyntheticFiles {

  public static final String EICAR =
    "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

  private SyntheticFiles() {
  }

  /**
   * Conteúdo válido do formato da extensão, com exatamente {@code size} bytes.
   */
  public static byte[] of(final String filename, final int size) {
    if (size == 0) {
      return new byte[0];
    }
    final var extension = extensionOf(filename);
    return switch (extension) {
      case "pdf" -> pdf(size, "% arquivo " + filename);
      case "jpg", "jpeg" -> jpeg(size);
      case "png" -> png(size);
      default -> text(extension, size);
    };
  }

  /**
   * Conteúdo determinístico por nome (mesmo nome → mesmos bytes → mesmo SHA-256),
   * para os cenários de idempotência (RF07).
   */
  public static byte[] deterministicPdf(final String filename) {
    return pdf(2048, "% conteudo deterministico de " + filename);
  }

  /**
   * PDF estruturalmente válido contendo a assinatura EICAR num comentário.
   */
  public static byte[] pdfWithEicar() {
    return pdf(2048, "%" + EICAR);
  }

  /**
   * PDF truncado: assinatura {@code %PDF} presente, trailer {@code %%EOF} ausente.
   */
  public static byte[] truncatedPdf() {
    final var header = "%PDF-1.4\n% documento interrompido no meio".getBytes(StandardCharsets.US_ASCII);
    final var garbage = new byte[512];
    Arrays.fill(garbage, (byte) 0x07);
    final var out = new ByteArrayOutputStream();
    out.writeBytes(header);
    out.writeBytes(garbage);
    return out.toByteArray();
  }

  /**
   * Executável DOS/Windows (magic {@code MZ}) — Tika detecta {@code application/x-msdownload}.
   */
  public static byte[] windowsExecutable() {
    final var out = new ByteArrayOutputStream();
    out.writeBytes("MZ".getBytes(StandardCharsets.US_ASCII));
    final var padding = new byte[1022];
    Arrays.fill(padding, (byte) 0x90);
    out.writeBytes(padding);
    return out.toByteArray();
  }

  private static byte[] pdf(final int size, final String comment) {
    final var header = "%PDF-1.4\n" + comment + "\n";
    final var trailer = "\n%%EOF";
    return padded(header.getBytes(StandardCharsets.US_ASCII), trailer.getBytes(StandardCharsets.US_ASCII),
      size, (byte) 'x');
  }

  private static byte[] jpeg(final int size) {
    final var header = new byte[]{
      (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10,
      'J', 'F', 'I', 'F', 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00
    };
    final var trailer = new byte[]{(byte) 0xFF, (byte) 0xD9};
    return padded(header, trailer, size, (byte) 0x00);
  }

  private static byte[] png(final int size) {
    final var header = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    final var trailer = new byte[]{0x00, 0x00, 0x00, 0x00, 'I', 'E', 'N', 'D', (byte) 0xAE, 'B', '`', (byte) 0x82};
    return padded(header, trailer, size, (byte) 0x00);
  }

  private static byte[] text(final String extension, final int size) {
    final var seed = switch (extension) {
      case "csv" -> "coluna_a,coluna_b,coluna_c\nvalor_1,valor_2,valor_3\n";
      case "json" -> "{\"itens\":[\"graphrag\",\"ingestao\",\"validacao\"],\"pad\":\"";
      case "xml" -> "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root><item>graphrag</item>\n";
      case "md" -> "# Notas do GraphRAG\n\nConteudo de teste da suite BDD.\n";
      default -> "conteudo textual de teste da suite BDD do GraphRAG\n";
    };
    final var out = new ByteArrayOutputStream();
    final var seedBytes = seed.getBytes(StandardCharsets.US_ASCII);
    while (out.size() < size) {
      out.write(seedBytes, 0, Math.min(seedBytes.length, size - out.size()));
    }
    return out.toByteArray();
  }

  private static byte[] padded(final byte[] header, final byte[] trailer, final int size, final byte filler) {
    final var minimum = header.length + trailer.length;
    if (size < minimum) {
      throw new IllegalArgumentException("Tamanho %d insuficiente para o formato (mínimo %d)".formatted(size, minimum));
    }
    final var content = new byte[size];
    System.arraycopy(header, 0, content, 0, header.length);
    Arrays.fill(content, header.length, size - trailer.length, filler);
    System.arraycopy(trailer, 0, content, size - trailer.length, trailer.length);
    return content;
  }

  private static String extensionOf(final String filename) {
    final var dot = filename.lastIndexOf('.');
    return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase();
  }

}
