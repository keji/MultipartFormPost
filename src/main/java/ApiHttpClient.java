import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.protocol.HTTP;
import org.json.JSONObject;

public class ApiHttpClient {

	public static JSONObject newMultipartFormPost(String url,
			Map<String, Object> params, ProgressListener progressListener)
			throws Exception {
		HttpURLConnection urlConnection = null;
		OutputStream out = null;
		InputStream in = null;
		try {
			urlConnection = createPostHttpConnection(url);
			MultipartEntity reqEntity = new CustomMultipartEntity(
					org.apache.http.entity.mime.HttpMultipartMode.BROWSER_COMPATIBLE,
					progressListener);
			if (params != null && params.size() > 0) {
				Set<String> keys = params.keySet();
				Iterator<String> iter = keys.iterator();
				while (iter.hasNext()) {
					String key = (String) iter.next();
					Object obj = params.get(key);
					// 如果是文件类型
					if (obj instanceof File) {
						FileBody uploadFile = new FileBody((File) obj);
						reqEntity.addPart(key, uploadFile);
						// 如果是字符串
					} else if (obj instanceof String) {
						try {
							StringBody str = new StringBody((String) obj,
									Charset.forName(HTTP.UTF_8));
							reqEntity.addPart(key, str);
						} catch (UnsupportedEncodingException e) {
						}
					}
				}
			}
			
			urlConnection.addRequestProperty("Content-length",
					reqEntity.getContentLength() + "");
			// urlConnection.setFixedLengthStreamingMode(reqEntity.getContentLength());
			urlConnection.addRequestProperty(reqEntity.getContentType()
					.getName(), reqEntity.getContentType().getValue());
			out = new BufferedOutputStream(urlConnection.getOutputStream());
			reqEntity.writeTo(out);
			out.flush();
			out.close();
			out = null;
			urlConnection.connect();
			JSONObject json = null;
			if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
				in = new BufferedInputStream(urlConnection.getInputStream());
				String result = new String(readInputStream(in));
				json = new JSONObject(result);
				return json;
			} else {
				in = new BufferedInputStream(urlConnection.getErrorStream());
				String result = new String(readInputStream(in));
				throw new Exception(result);
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new Exception(e);
		} finally {

			if (out != null)
				out.close();
			if (in != null)
				in.close();
			if (urlConnection != null)
				urlConnection.disconnect();
		}
	}

	// 添加签名header
	public static HttpURLConnection createPostHttpConnection(String uri)
			throws IOException {
		URL url = new URL(uri);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setUseCaches(false);
		conn.setDoInput(true);
		conn.setDoOutput(true);
		conn.setRequestMethod("POST");
		// conn.setInstanceFollowRedirects(true);
		conn.setReadTimeout(60000);
		conn.setConnectTimeout(30000);
		// conn.setRequestProperty("Connection", "Keep-Alive");
		conn.setRequestProperty("Connection", "close");
		conn.setRequestProperty("Cache-Control", "no-cache");
		// conn.setRequestProperty("Content-Type", "multipart/form-data");
		return conn;
	}

	public static byte[] readInputStream(InputStream inStream) throws Exception {
		ByteArrayOutputStream outStream = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int len = 0;
		while ((len = inStream.read(buffer)) != -1) {
			outStream.write(buffer, 0, len);
		}
		byte[] data = outStream.toByteArray();
		outStream.close();
		return data;
	}

}

class CustomMultipartEntity extends MultipartEntity {
	private final ProgressListener listener;

	public CustomMultipartEntity(final ProgressListener listener) {
		super();
		this.listener = listener;
	}

	public CustomMultipartEntity(final HttpMultipartMode mode,
			final ProgressListener listener) {
		super(mode);
		this.listener = listener;
	}

	public CustomMultipartEntity(HttpMultipartMode mode, final String boundary,
			final Charset charset, final ProgressListener listener) {
		super(mode, boundary, charset);
		this.listener = listener;
	}

	@Override
	public void writeTo(OutputStream outstream) throws IOException {
		super.writeTo(new CountingOutputStream(outstream, this.listener));
	}

}

class CountingOutputStream extends FilterOutputStream {
	private final ProgressListener listener;
	private long transferred;

	public CountingOutputStream(final OutputStream out,
			final ProgressListener listener) {
		super(out);
		this.listener = listener;
		this.transferred = 0;
	}

	public void write(byte[] b, int off, int len) throws IOException {
		out.write(b, off, len);
		this.transferred += len;
		this.listener.transferred(this.transferred);
	}

	public void write(int b) throws IOException {
		out.write(b);
		this.transferred++;
		this.listener.transferred(this.transferred);
	}
}

interface ProgressListener {
	void transferred(long num);
}
