package prof7bit.torchat.core;

import java.io.IOException;

import prof7bit.reactor.Reactor;
import android.util.Log;

/**
 * this class represent one chat handler for handshake handling, and received
 * and sending messages
 * 
 * @author busylee demonlee999@gmail.com
 * 
 */
public class Buddy implements ConnectionHandler {
	final static String LOG_TAG = "Buddy";

	Connection mIncomingConnection = null;
	Connection mOutcomingConnection = null;

	public String mOnionAddressRecepient = null;

	Client mClient = null;

	boolean mHandshakeComlete = false;

	public Buddy(Client client) {
		mClient = client;
	}

	@Override
	public void onPingReceived(Msg_ping msg) {
		logInfo(" ping " + msg.getOnionAddress() + " " + msg.getRandomString());
		// store onion address if null
		if (mOnionAddressRecepient == null)
			mOnionAddressRecepient = msg.getOnionAddress();

		/*
		 * if outcoming connection is null start new outcoming connection
		 */
		if (mOutcomingConnection == null) {
			try {
				Connection connection;

				connection = new Connection(new Reactor(),
						msg.getOnionAddress() + Client.ONION_DOMAIN,
						Client.TORCHAT_DEFAULT_PORT, mClient);
				
				//store this conection
				addOutcomingConnection(connection);

				connection.recipientOnionAddress = msg.getOnionAddress();

				// send ping
				Msg_ping msgPing = new Msg_ping(connection);
				msgPing.setOnionAddress(mClient.mMyOnionAddress);
				msgPing.setRandomString(mClient.mMyRandomString);
				connection.sendMessage(msgPing);

				// send message 'pong"
				Msg_pong msgPong = new Msg_pong(connection);
				msgPong.setRandomString(msg.getRandomString());
				connection.sendMessage(msgPong);

				// send message "status"
				Msg_status msgStatus = new Msg_status(connection);
				msgStatus.setAvailiable();
				connection.sendMessage(msgStatus);

				// send message "version" for appearing online
				Msg_version msgVersion = new Msg_version(connection);
				connection.sendMessage(msgVersion);

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} // end if
		else {
			/*
			 * if there is outcoming connection we must send "pong" first of all
			 */
			// send message 'pong"
			Msg_pong msgPong = new Msg_pong(mOutcomingConnection);
			msgPong.setRandomString(msg.getRandomString());
			mOutcomingConnection.sendMessage(msgPong);

			// send message "status"
			Msg_status msgStatus = new Msg_status(mOutcomingConnection);
			msgStatus.setAvailiable();
			mOutcomingConnection.sendMessage(msgStatus);

			// send message "version" for appearing online
			Msg_version msgVersion = new Msg_version(mOutcomingConnection);
			mOutcomingConnection.sendMessage(msgVersion);
		}

		logInfo("onPingReceived");
	}

	@Override
	public void onPongReceived(Msg_pong msg) {
		logInfo(" pong " + msg.getRandomString());
		mHandshakeComlete = true;
		mClient.onChatEstablished(mOnionAddressRecepient);
	}

	@Override
	public void onMessageReceived(Msg_message msg) {
		logInfo("onMessageReceived");
		mClient.onMessage(mOnionAddressRecepient, msg.getMessage());
	}

	@Override
	public void onStatusReceived(Msg_status msg) {
		logInfo("onStatusReceived");
	}

	@Override
	public void onConnect(Connection connection) {
		logInfo(connection.getStringConnectionType() + " onConnect");
	}

	@Override
	public void onDisconnect(Connection connection, String reason) {
		logInfo(connection.getStringConnectionType() + " onDisconnect: "
				+ reason);
		
	}

	protected void logInfo(String text) {
		Log.i(LOG_TAG + "/" + (mOnionAddressRecepient != null ? mOnionAddressRecepient
				: "undefinedOnionAddress"), text);
	}

	/**
	 * store incoming connection
	 * 
	 * @param incomingConnection
	 */
	public void addIncomingConnection(Connection incomingConnection) {
		/*
		 * TODO need to close connection because we lost link to this object
		 * now, socket must be closed, check f connection not null
		 */
		mIncomingConnection = incomingConnection;
		mIncomingConnection.setConnectionHandler(this);
	}

	/**
	 * store outcoming connection
	 * 
	 * @param outcomingConnection
	 */
	public void addOutcomingConnection(Connection outcomingConnection) {
		/*
		 * TODO need to close connection because we lost link to this object
		 * now, socket must be closed, check f connection not null
		 */
		mOutcomingConnection = outcomingConnection;
		mOutcomingConnection.setConnectionHandler(this);
	}

	/**
	 * check is this buddy for given onion address
	 * 
	 * @param onionAddress
	 * @return
	 */
	public boolean isOnionAddressLike(String onionAddress) {

		if (mOnionAddressRecepient == null) {
			Log.w(LOG_TAG, "onion address of recipient is null");
			return false;
		}

		if (onionAddress == null) {
			Log.w(LOG_TAG, "getted onion address is null");
			return false;
		}
		return mOnionAddressRecepient.equals(onionAddress);
	}

	/**
	 * check there is outcoming connection for this buddy TODO may be need to
	 * change this logic, connection may be closed already, or refused
	 * 
	 * @return
	 */
	public boolean hasOutComingConnection() {
		return mOutcomingConnection != null;
	}

	/**
	 * check there is incoming connection for this buddy TODO may be need to
	 * change this logic, connection may be closed already, or refused
	 * 
	 * @return
	 */
	public boolean hasInComingConnection() {
		return mIncomingConnection != null;
	}

	/**
	 * check is buddy ready for chat. I assume buddy is ready for chat if: it
	 * has 2 connections incoming and outcoming it has onion address of
	 * recipient handshake is complete
	 * 
	 * @return
	 */
	public boolean isReadyForChat() {
		return hasInComingConnection() && hasOutComingConnection()
				&& mOnionAddressRecepient != null && mHandshakeComlete;
	}

	/*********************
	 * GETTERS AND SETTERS
	 ********************/
	public String getOnionAddressRecepient() {
		return mOnionAddressRecepient;
	}

	public void setOnionAddressRecepient(String onionAddressRecepient) {
		this.mOnionAddressRecepient = onionAddressRecepient;
	}
}
