package tests.wurstscript.objectreader;

import static org.testng.Assert.assertEquals;

import com.google.common.base.Charsets;
import de.peeeq.wurstio.objectreader.BinaryDataInputStream;
import de.peeeq.wurstio.objectreader.BinaryDataOutputStream;
import de.peeeq.wurstscript.WLogger;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.testng.annotations.Test;

public class BinaryDataStreamsTest {

  @Test
  public void testWriteInt() throws IOException {
    try (ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        BinaryDataOutputStream out = new BinaryDataOutputStream(outStream, true)) {
      int[] numbers = {
        1 << 23, 1, 2, 3, 0, 1 << 8, 1 << 16, 1 << 24, Integer.MAX_VALUE, Integer.MIN_VALUE
      }; // getRandomInts();
      for (int i = 0; i < numbers.length; i++) {
        out.writeInt(numbers[i]);
      }
      // 2 0000 000 0000
      out.flush();

      WLogger.info("bytes = " + outStream);
      byte[] bytes = outStream.toByteArray();

      try (BinaryDataInputStream in =
          new BinaryDataInputStream(new ByteArrayInputStream(bytes), true)) {
        for (int i = 0; i < numbers.length; i++) {
          int read = in.readInt();
          WLogger.info("read = " + read);
          assertEquals(numbers[i], read);
        }
      }
    }
  }

  @Test
  public void testWriteString() throws IOException {
    try (ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        BinaryDataOutputStream out = new BinaryDataOutputStream(outStream, true)) {

      String s = "Hello Wörld!";
      out.writeString(s, s.length() + 1); // +1 because ö takes one byte
      // more
      out.flush();
      byte[] bytes = outStream.toByteArray();

      try (BinaryDataInputStream in =
          new BinaryDataInputStream(new ByteArrayInputStream(bytes), true)) {
        String s2 = in.readString(s.length() + 1);
        assertEquals(s, s2);
      }
    }
  }

  @Test
  public void testWriteStringNullTerminated() throws IOException {
    try (ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        BinaryDataOutputStream out = new BinaryDataOutputStream(outStream, true)) {

      String s = "Hello Wörld!";
      out.writeStringNullTerminated(s, Charsets.UTF_8);
      out.flush();
      byte[] bytes = outStream.toByteArray();

      for (byte b : bytes) {
        WLogger.info(b + " " + (b >= 0 ? (char) b : ""));
      }

      try (BinaryDataInputStream in =
          new BinaryDataInputStream(new ByteArrayInputStream(bytes), true)) {
        String s2 = in.readNullTerminatedString(Charsets.UTF_8);
        assertEquals(s.length(), s2.length());
        assertEquals(s, s2);
      }
    }
  }

  @Test
  public void testWriteFloat() throws IOException {
    try (ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        BinaryDataOutputStream out = new BinaryDataOutputStream(outStream, true)) {

      float f = 1.234f;
      out.writeFloat(f);
      out.flush();
      byte[] bytes = outStream.toByteArray();

      try (BinaryDataInputStream in =
          new BinaryDataInputStream(new ByteArrayInputStream(bytes), true)) {
        float f2 = in.readFloat();
        assertEquals(f, f2, 0.0);
      }
    }
  }
}
