//mvn exec:java -Dexec.mainClass=”bgu.spl171.net.impl.TFTPtpc.TPCMain” -Dexec.args=”8888”


package bgu.spl171.net.impl.packets;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;

import bgu.spl171.net.api.bidi.BidiMessagingProtocol;
import bgu.spl171.net.srv.bidi.Connections;
import bgu.spl171.net.srv.bidi.ConnectionsImpl;

public class PacketsProtocol implements BidiMessagingProtocol<Packet> {

	private int id;
	private ConnectionsImpl<Packet> connections;
	private LinkedList<DATAPacket> sentDataList;
	private short waitingForACK = -1;
	private short waitingForData = -1;
	private File writtenFile;
	private boolean shouldTerminate;

	@Override
	public void start(int connectionId, Connections<Packet> connections) {
		id = connectionId;
		this.connections = (ConnectionsImpl<Packet>) connections;
		shouldTerminate = false;
	}

	@Override
	public void process(Packet message) {
		System.out.println("A packet " + message.opCode() + " just arrived");
		if (message.opCode() < 1 || message.opCode() > 10)
			send(new ERRORPacket(4, "unkown opcode"));
		if (connections.isLoggedIn(id) || message.opCode()==7) {
			switch (message.opCode()) {
			case 1:
				RRQProcess((RRQPacket) message);
				break;
			case 2:
				WRQProcess((WRQPacket) message);
				break;
			case 3:
				DATAProcess((DATAPacket) message);
				break;
			case 4:
				ACKProcess((ACKPacket) message);
				break;
			case 5:
				ERRORProcess((ERRORPacket) message);
				break;
			case 6:
				DIRQProcess((DIRQPacket) message);
				break;
			case 7:
				LOGRQProcess((LOGRQPacket) message);
				break;
			case 8:
				DELRQProcess((DELRQPacket) message);
				break;
			case 9:
				BCASTProcess((BCASTPacket) message);
				break;
			case 10:
				DISCProcess((DISCPacket) message);
				break;
			default:
				break;
			}
		} else {
			send(new ERRORPacket(6, "user is not logged in"));
		}
	}

	private void DISCProcess(DISCPacket message) {
		System.out.println("Client " +id + " left us here all alone! ):");
		send(new ACKPacket());
		connections.disconnect(id);	// not sure if it should happen here
		shouldTerminate = true;	// maybe only this thing?
	}

	private void BCASTProcess(BCASTPacket message) {
		send(new ERRORPacket(4, "This is a server-to-client packet only. where did you get it?"));
	}

	private void DELRQProcess(DELRQPacket message) {
		File file = stringToFile(message.filename());
		if (isFileExists(file)) {
			file.delete();
			send(new ACKPacket());
			connections.broadcast(new BCASTPacket(false, message.filename()));
				System.out.println("I never liked " + message.filename() + " anyways...");
		} else
			send(new ERRORPacket(1, "file not found"));
	}

	private void LOGRQProcess(LOGRQPacket message) {
		if (connections.isLoggedIn(id))
			send(new ERRORPacket(7, "user already logged in"));
		else if (connections.isUsernameExists(message.username()))
			send(new ERRORPacket(7, "username already exists"));
		else {
			connections.login(id, message.username());
			send(new ACKPacket());
				System.out.println("Yay! " + message.username() + " joined in!");
		}
	}

	private void DIRQProcess(DIRQPacket message) {
		String filesnames = "";
		File folder = new File("Files");
		File[] filesList = folder.listFiles();
		if (filesList.length == 0){		// if there are no files, send an empty packet
			DATAPacket dataPack = new DATAPacket((short)0, (short)1, new byte[0]);
			send(sentDataList.removeFirst());
			waitingForACK = dataPack.blockNum();
		}
		else{
			for (int i = 0; i < filesList.length; i++) {
				if (filesList[i].canRead())
					filesnames += filesList[i].getName() + '\0';
			}
			// and now I will make it into data packs and send it:
			try {
				System.out.println("filenames: " + filesnames);
				sentDataList = createDataList(new ByteArrayInputStream(filesnames.getBytes("UTF-8")));
				DATAPacket dataPack = sentDataList.poll();
				send(dataPack);
				waitingForACK = dataPack.blockNum();
					System.out.println("I just sent " +id + " packet number " + waitingForACK);
				// the rest of the process will take place in the ACKprocess
			} catch (IOException e) {
				send(new ERRORPacket(2, "an IO exception occured"));
			}
		}
	}

	private void ERRORProcess(ERRORPacket message) { // in that case, we reset the RRQ/WRQ process
		waitingForACK = -1; 
		waitingForData = -1;
			System.out.println(id + " just sent an error. I hope he's OK  0:");
	}

	private void DATAProcess(DATAPacket message) {
		if (waitingForData == message.blockNum()) try{ //if this is indeed the data packet we expected...
			FileOutputStream fos = new FileOutputStream(writtenFile);
			fos.write(message.data());
			fos.close();
			send(new ACKPacket(message.blockNum()));
				System.out.println("Data packet " + message.blockNum() + " is finally here!");
			if (message.packetSize() == 512){ // that means it's not the last packet
				waitingForData++;
			}
			else if (message.packetSize() < 512){ // that means it's the last packet
				waitingForData = -1;
				Files.move(writtenFile.toPath(), Paths.get("Files\\" + writtenFile.getName()));
				// FIX PATH!!!!!!!!!!!!!!!!!!!!
				//											 FIX PATH!!!!!!!!!!!!!!!!!!!!
				// FIX PATH!!!!!!!!!!!!!!!!!!!!
				System.out.println("file moved");
				try {Files.delete(Paths.get("tmp"));}	catch(IOException e){} // if the tmp folder is empty, delete it
				connections.broadcast(new BCASTPacket(true, writtenFile.getName()));
					System.out.println("HOORAH! now we have " + writtenFile.getName() + "! I always wanted one :D");
				writtenFile = null;
			}
			else			
				send(new ERRORPacket(4, "DATA packet is illegaly big!"));
		}
		catch (IOException e) { send(new ERRORPacket(2, "an unexpected IO exception occured")); }
		else
			send(new ERRORPacket(4, "an unexpected DATA packet " + message.blockNum() + " arrived. expected DATA number: "+waitingForData));
	}

	private void ACKProcess(ACKPacket message) { // this process is mostly a continuation of the RRQ process
	//	System.out.println("Ack pack " + message.BlockNum());
		if (waitingForACK == message.BlockNum()) { // if this is indeed the packet we expected...
			if (!sentDataList.isEmpty()) {
				DATAPacket dataPack = sentDataList.poll();
				send(dataPack);
	//				System.out.println("Sending Data number " + waitingForACK);
				waitingForACK = dataPack.blockNum();
	//			System.out.println("waiting for ack " + waitingForACK);
			} else{
				waitingForACK = -1; // if we sent all the data packets we are not waiting for an ACK anymore
				System.out.println("done transfer");
			}
		} else
			send(new ERRORPacket(0, "unexpected ACKPACKET. block # " + waitingForACK + " was expected"));
	}

	private void WRQProcess(WRQPacket message) {
		File file = stringToTempFile(message.filename());
		try {
			if (new File("Files" , file.getName()).exists())
				send(new ERRORPacket(5, "file already exists!"));
			else if (!file.createNewFile())
				send (new ERRORPacket(5, "Another user is currently writing a file with the same name"));
			else {
				waitingForData = 1;
				writtenFile = file;
				send(new ACKPacket());
					System.out.println("Client" + id + " wants to give us " + message.filename() + " for free! OMG I'm so excited!");
				// the rest of the process will take place in the DATAprocess
			}
		}
		catch (IOException e) { send(new ERRORPacket(2, "an unexpected IO exception occured")); }
	}

	private void RRQProcess(RRQPacket message) {
		File file = stringToFile(message.filename());
		if (isFileExists(file) && file.canRead()) {
			try {
				sentDataList = createDataList(new ByteArrayInputStream(Files.readAllBytes(file.toPath())));
				DATAPacket dataPack = sentDataList.poll();
				send(dataPack);
				waitingForACK = dataPack.blockNum();
					System.out.println("Client " + id + " asked for " + message.filename() + ". I like his taste.");
					System.out.println("I sent packet " + dataPack.blockNum());
				// the rest of the process will take place in the ACKprocess
			} catch (IOException e) {
				send(new ERRORPacket(2, "an IO exception occured"));
			}
		} else {
			send(new ERRORPacket(1, "required file not found"));
		}
	}

	/** helping functions: **/

	/**
	 * Creates a temporary file in the "tmp" folder.
	 * @param filename: The name of the file to create
	 * @return: the requested file
	 */
	private File stringToTempFile(String filename) {
		if (!Files.exists(Paths.get("tmp")))
			new File("tmp").mkdir(); // This is the folder where we will handle our new files before they are ready
		return (new File("tmp", filename)); // IMPORTANT: REMEMBER TO FIX
	}
	
	/**
	 * creates a file object of the "Files" folder for searching
	 * @param filename the name of the file
	 * @return the file
	 */
	private File stringToFile(String filename) {
		return (new File("Files", filename)); // IMPORTANT: REMEMBER TO FIX
	}

	private boolean isFileExists(File f) {
		return (f.exists());
	}

	private void send(Packet pack) {
		connections.send(id, pack);
	}

	/**
	 * Creates a list of up to 512-byte size packets from a ByteArrayInputStream
	 * @param reader an input stream the data of the packets would be collected from 
	 * @return a list of data packets, each of 512 bytes except the last one
	 * @throws IOException if a problem occurs with the reader
	 */
	private LinkedList<DATAPacket> createDataList(ByteArrayInputStream reader) throws IOException {
		System.out.println("reader size: " + reader.available());
		LinkedList<DATAPacket> dataList = new LinkedList<DATAPacket>();
		short read;
		short block = 1;
		do { // move the data to chunks of up to 512 bytes each and place
				// them in the data queue
			byte[] dataChunk = new byte[512];
			read = (short) reader.read(dataChunk);
			if(read==-1) 
				read = 0;
			dataList.add(new DATAPacket(read, block++, dataChunk));
			if(block<20)
				System.out.println("data number " + (int)(block -1));
//			System.out.println("we read " + read);
		} while (read == 512);
// *when we test everything, we need to check what happens in case of a 512-bytes size. I expect to have another empty array in that case*
		// I will now fix the size of the last data chunk:
		if (read > 0){
			byte[] lastChunk = new byte[read];
			for (int j = 0; j < lastChunk.length; j++) {
				lastChunk[j] = dataList.peekLast().data()[j];
			}
			dataList.peekLast().setData(lastChunk);
		}
		else
			dataList.peekLast().setData(new byte[0]);			
		System.out.println("we have " + dataList.size() + " packets");
		return dataList;
}
	

	@Override
	public boolean shouldTerminate() {
		return shouldTerminate;
	}

}
