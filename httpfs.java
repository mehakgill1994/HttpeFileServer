import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;


public class httpfs {

	public static int port = 8080;
	private static boolean isVerbose = false;
	private static String directory = "../";
	private static String response = "";

	static public class StartServer implements Runnable {

		@Override
		public void run() {

		}
	}

	public static void main(String[] args) {

		Thread startListening = new Thread(new StartServer());
		startListening.start();

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-v")) {
				isVerbose = true;
			}
			if (args[i].equals("-p")) {
				port = Integer.parseInt(args[i + 1]);
			}
			if (args[i].equals("-d")) {
				directory = args[i + 1];
			}
		}

		ServerSocket server = null;
		BufferedWriter bufferWriter = null;
		try {
			server = new ServerSocket(port);
			while (true) {
				System.out.println("listening at "+port);
				Socket socket = server.accept();
				
				byte[] buffer = new byte[1000];
				socket.getInputStream().read(buffer);
				String request = new String(buffer);
				
				
				
				bufferWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
				
				if(isVerbose) {
				response += "HTTP/1.0 200 OK\r\n";
				response += "Content-Type: application/json\r\n";
				}
				response += "\r\n";
				parseRequest(request);
				
				
				System.out.println("Request Handled.");
				System.out.println("Response forwarded to Client.");
				
				bufferWriter.write(response);
				response = "";
				bufferWriter.flush();
				bufferWriter.close();
				
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void parseRequest(String request) {

		String[] requestLines = request.trim().split("\n");
		String typeOfRequest = requestLines[0].split(" ")[0];
		String fileName = requestLines[0].split(" ")[1];
		String fileType = "";
		boolean isFile = true;

		if (fileName.trim().equals("/")
				|| fileName.trim().equals("../") /* second condition implementation pending, maybe we'll need */) {
			isFile = false;
		}
		
		if (typeOfRequest.equalsIgnoreCase("get") && requestLines.length == 3) {
			fileType = requestLines[2].split(":")[1].trim();
		} else if (typeOfRequest.equalsIgnoreCase("get") && requestLines.length == 2) {
			fileType = "text";
		}

		String body = "";
		boolean bodyStarted = false;
		for (String line : requestLines) {
			if (bodyStarted) {
				body += line;
			}
			if (line.length() == 1) {
				bodyStarted = true;
			}
		}
		handleRequest(typeOfRequest, isFile, fileName, body, fileType);
	}

	private static void handleRequest(String typeOfRequest, boolean isFile, String fileName, String body, String fileType) {

		File file;
		File[] files;

		try {
			switch (typeOfRequest.toLowerCase()) {

			case "get":
				if (isFile) {
					file = new File(directory + fileName);
					if (file.exists()) {
						openFile(fileName, fileType, file);
					}
					else {
						response += "HTTP ERROR 404\nFILE NOT FOUND";
					}
				} else {
					files = new File(directory).listFiles();
					for (int i = 0; i < files.length; i++) {
						response += files[i].getName() + "\n";
					}
				}
				break;
			case "post":
				RandomAccessFile fileToWrite = new RandomAccessFile(directory + fileName, "rw");
				FileChannel channel = fileToWrite.getChannel();
				FileLock lock = null;
				try {
					lock = channel.tryLock();
				} catch (final OverlappingFileLockException e) {
					System.out.println("Cannot access the file while the other client is writing to the file");
					fileToWrite.close();
					channel.close();
				}
				fileToWrite.writeChars(body);
				TimeUnit.SECONDS.sleep(20);
				lock.release();
				fileToWrite.close();
				channel.close();
				break;

			default:
				response += "INVALID REQUEST";
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	public static void openFile(String fileName, String fileType, File file) {
		
		switch (fileType) {
		case "text":
			BufferedReader fileReader;
			try {
				response += "reading " + fileName + "...\nContent:";
				fileReader = new BufferedReader(new FileReader(file));
				String allLines = "", line;
				while ((line = fileReader.readLine()) != null) {
					allLines += line+"\n";
				}
				response += allLines;
			} catch (Exception e) {
				e.printStackTrace();
			}			
			break;
			
		case "image":
			SwingUtilities.invokeLater(new Runnable()
		    {
		      public void run()
		      {
		        JFrame editorFrame = new JFrame("Image Demo");
		        editorFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		        
		        BufferedImage image = null;
		        try
		        {
		          image = ImageIO.read(new File(directory+fileName));
		        }
		        catch (Exception e)
		        {
		          e.printStackTrace();
		          System.exit(1);
		        }		        

		        ImageIcon imageIcon = new ImageIcon(image);
		        JLabel jLabel = new JLabel();
		        jLabel.setIcon(imageIcon);
		        editorFrame.getContentPane().add(jLabel, BorderLayout.CENTER);

		        editorFrame.pack();
		        editorFrame.setLocationRelativeTo(null);
		        editorFrame.setVisible(true);
		      }
		    });
			break;

		case "pdf":
	        if (Desktop.isDesktopSupported()) {
	            try {
	                File myFile = new File(directory+fileName);
	                Desktop.getDesktop().open(myFile);
	            } catch (IOException ex) {
	                ex.printStackTrace();
	            }
	        }
			break;
			
		default:
			response += "HTTP ERROR 404\nFILE NOT FOUND";
			break;
		}
	}
}
