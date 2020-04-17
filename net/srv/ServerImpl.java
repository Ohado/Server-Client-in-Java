package bgu.spl171.net.srv;

import bgu.spl171.net.api.MessageEncoderDecoder;
import bgu.spl171.net.api.bidi.BidiMessagingProtocol;
import java.util.function.Supplier;

public class ServerImpl<T> extends BaseServer<T> {

	// I made a new server copied mostly from BaseServer. I didn't extend BaseServer because it "done me the death" with T->T problems


    public ServerImpl(
            int port,
            Supplier<BidiMessagingProtocol<T>> protocolFactory,
            Supplier<MessageEncoderDecoder<T>> encdecFactory) {
		super(port, protocolFactory, encdecFactory);
		System.out.println("Server ready");
    }

    protected void execute(BlockingConnectionHandler<T>  handler){
    	Thread thread = new Thread(handler);
    	getConnections().connect(handler, handler.getId()); //are we allowed to do it here? I hope its fine
    	thread.start();
    }

}
