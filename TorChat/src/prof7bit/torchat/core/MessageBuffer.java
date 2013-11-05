package prof7bit.torchat.core;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

/**
 * This class is used when parsing a raw incoming message or when serializing 
 * an outgoing message. It wraps a byte array for the serialized message data 
 * and has methods to read and write parts of it and for handling TorChat's 
 * transfer-encoding format.      
 *
 * @author Bernd Kreuss <prof7bit@gmail.com>
 *
 */
public class MessageBuffer extends ByteArrayOutputStream{
	
	private int posRead = 0;
	
	/**
	 * Constructor used when creating a new message for sending
	 */
	public MessageBuffer(){
		super(256); // don't worry, it can grow automatically if needed
	}
	
	/**
	 * Constructor used when creating a message from raw incoming data. The
	 * binary decoding of the content will be performed by this constructor. 
	 * After this the message contents can be read.
	 *  
	 * @param buf byte[] with exactly(!) one transfer-encoded message.
	 */
	public MessageBuffer(byte[] buf){
		super(buf.length);
		reset();
		decodeFromReceived(buf);
		resetReadPos();
	}
	
	/**
	 * Write a string to the buffer. If this is not the first write then write 
	 * an additional leading space (0x20) before actually writing the data.
	 * Line breaks will be normalized to Unix line breaks (0x0a) and unicode 
	 * characters will be encoded UTF-8. 
	 * 
	 * @param s String may be unicode and may contain line breaks
	 */
	public void writeString(String s){
		byte[] b = encodeString(s); 
		writeBytes(b);
	}

	/**
	 * Write string representation of decimal integer. If this is not the 
	 * first write then write an additional leading space (0x20) before 
	 * actually writing the data.
	 *  
	 * @param n integer number to be written
	 */
	public void writeDecimal(int n){
		writeString(String.format("%d", n));
	}
	
	/**
	 * Write binary data. If this is not the first write then write an
	 * additional leading space (0x20) before actually writing the data.
	 * The data is written exactly as is, no transformations take place.
	 *  
	 * @param b byte[] containing the data to write
	 */
	public void writeBytes(byte[] b){
		if (count > 0){
			write(0x20);
		}
		write(b, 0, b.length);
	}

	/**
	 * Read from current position until the next space (0x20) and return 
	 * a byte array. current position will be advanced to the position
	 * after the space. The space is not included in the returned array.
	 * It can also read empty strings (when 2 or more consecutive spaces 
	 * appear then it will read an empty string and advance the position
	 * by 1, quasi reading the empty strings "between" the spaces).
	 * If there is no more space until the end it will read until the end.
	 * If position is at the end (nothing more to read) it will throw EOFException.
	 * 
	 * @return newly allocated byte[] containing the read bytes 
	 * @throws EOFException if no more bytes to read
	 */
	public byte[] readBytes() throws EOFException{
		int posDelimiter = posRead;
		if (posDelimiter >= count){
			throw new EOFException("no more bytes to read");
		}
		while (posDelimiter < count){
			if (this.buf[posDelimiter] == ' '){
				break;
			}
			posDelimiter++;
		}
		int lenRead = posDelimiter - posRead;
		return readNBytes(lenRead, 1);
	}
	
	
	/**
	 * like readBytes but will always read all remaining bytes until the end
	 * 
	 * @return newly allocated byte[] containing the read bytes 
	 * @throws EOFException if no more bytes to read
	 */
	public byte[] readBytesUntilEnd() throws EOFException{
		return readNBytes(count - posRead, 1);
	}
	
	private byte[] readNBytes(int lenRead, int skip) throws EOFException{
		if ((lenRead < 0) | (lenRead + posRead > count)){
			throw new EOFException("no more bytes to read");
		}
		byte[] result = new byte[lenRead];
		System.arraycopy(buf, posRead, result, 0, lenRead);
		posRead += lenRead + skip;
		return result;
	}
	
	/**
	 * Read Sting until the next space (0x20) and advance the position to the
	 * place after that space. This method behaves exactly like readBytes() but 
	 * it will convert the read bytes into a String. The conversion to String 
	 * means it will assume UTF-8 encoding and try to decode it to unicode, 
	 * it will also trim() the string and normalize all line endings to 0x0a.
	 * If position is at the end (nothing more to read) it will throw EOFException.
	 * 
	 * @return String with read bytes.
	 * @throws EOFException if nothing more to read
	 */
	public String readString() throws EOFException{
		return decodeString(readBytes());
	}
	
	/**
	 * Read the command of the message or throw EOF if it is empty.
	 * This will also reset the read position and after it has returned the 
	 * position will be right at the beginning of the message data.  
	 * 
	 * @return String containing the command
	 * @throws EOFException if the command or the entire message is empty
	 */
	public String readCommand() throws EOFException{
		resetReadPos();
		String c = readString();
		if (c.length() == 0){
			throw new EOFException();
		}
		return c;
	}
	
	/**
	 * reset the read position to the beginning of the buffer.
	 */
	public void resetReadPos(){
		posRead = 0;
	}
	
	/**
	 * Apply the TorChat binary encoding to the message and append the message
	 * delimiter 0x0a. The returned ByteBuffer can be used for writing to a
	 * java.nio.Channel without any further processing.
	 *  
	 * @return ByteBufer with the encoded message, ready for sending
	 */
	public ByteBuffer encodeForSending(){
		// replace every \ with \/
		// replace every 0x0a with \n
		//
		// the 10% increase is a conservative estimate, statistically it will 
		// grow by less than 0.5%. Should it really ever happen to need more 
		// then the ByteArrayOutputStream has the ability to grow dynamically.
		ByteArrayOutputStream b1 = new ByteArrayOutputStream((int)(this.count * 1.1));
		for (int i=0; i<this.count; i++){
			byte b = this.buf[i];
			if (b == '\\'){ 
				b1.write('\\');
				b1.write('/');
			}else{
				if (b == 0x0a){
					b1.write('\\');
					b1.write('n');
				}else{
					b1.write(b);
				}
			}
		}
		b1.write(0x0a);
		return ByteBuffer.wrap(b1.toByteArray());
	}
	
	/**
	 * The constructor will automatically invoke this.
	 * Decode the TorChat binary decoding and write the 
	 * decoded binary data into this RawMessage object.
	 * 
	 * @param incomingBuf byte[] containing the encoded message
	 */
	private void decodeFromReceived(byte[] incomingBuf){
		// replace every \n with 0x0a
		// replace every \/ with \
		int pos = 0;
		while (pos < incomingBuf.length){
			byte b = incomingBuf[pos++];
			if ((b == '\\') && (pos < incomingBuf.length)){
				b = incomingBuf[pos++];
				if (b == 'n'){
					write(0x0a);
				}
				if (b == '/'){
					write('\\');
				}
			}else{
				write(b);
			}
		}
	}
	
	/**
	 * convert text string into UTF-8 encoded byte array. 
	 * Also normalize line endings to LF (0x0a) and remove
	 * all leading and trailing whitespace and line ends
	 */
	private byte[] encodeString(String s){
		try {
			return trimAndNormalize(s).getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			return "###string-encode-error###".getBytes();
		}
	}

	/**
	 * convert UTF-8 encoded byte array into unicode string. 
	 * Also normalize line endings to LF (0x0a) and remove
	 * all leading and trailing whitespace and line ends
	 */
	private String decodeString(byte[] b) {
		try {
			return trimAndNormalize(new String(b, "UTF-8"));
		} catch (UnsupportedEncodingException e) {
			return "###string-decode-error###";
		}
	}

	private String trimAndNormalize(String s){
		return s.trim().replaceAll("\\r\\n?", "\n");
	}
}
