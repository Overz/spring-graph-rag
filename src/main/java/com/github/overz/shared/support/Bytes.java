package com.github.overz.shared.support;

/**
 * Buscas binárias simples compartilhadas pelos validadores e pelo scanner de malware —
 * evita duplicar varredura de assinatura em cada classe.
 */
public final class Bytes {

  private Bytes() {
  }

  public static boolean startsWith(final byte[] data, final byte[] prefix) {
    if (data.length < prefix.length) {
      return false;
    }
    for (var i = 0; i < prefix.length; i++) {
      if (data[i] != prefix[i]) {
        return false;
      }
    }
    return true;
  }

  public static boolean contains(final byte[] haystack, final byte[] needle) {
    return indexOf(haystack, needle) >= 0;
  }

  public static int indexOf(final byte[] haystack, final byte[] needle) {
    outer:
    for (var i = 0; i <= haystack.length - needle.length; i++) {
      for (var j = 0; j < needle.length; j++) {
        if (haystack[i + j] != needle[j]) {
          continue outer;
        }
      }
      return i;
    }
    return -1;
  }

}
