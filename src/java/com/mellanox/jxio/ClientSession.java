/*
 ** Copyright (C) 2013 Mellanox Technologies
 **
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at:
 **
 ** http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 ** either express or implied. See the License for the specific language
 ** governing permissions and  limitations under the License.
 **
 */
package com.mellanox.jxio;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import java.net.URI;

import com.mellanox.jxio.impl.Bridge;
import com.mellanox.jxio.impl.Event;
import com.mellanox.jxio.impl.EventMsgError;
import com.mellanox.jxio.impl.EventNewMsg;
import com.mellanox.jxio.impl.EventSession;

public class ClientSession extends EventQueueHandler.Eventable {

	private final Callbacks         callbacks;
	private final EventQueueHandler eventQHandler;
	private static final Log        LOG           = LogFactory.getLog(ClientSession.class.getCanonicalName());

	public static interface Callbacks {
		public void onReply(Msg msg);

		public void onSessionEstablished();

		public void onSessionEvent(EventName session_event, EventReason reason);

		public void onMsgError(Msg msg, EventReason reason);
	}
	
	/** ClientSession is the object that connects to the Server. The application sends requests to the server
	 * and receives responses. 
	 * 
	 * @param eventQHandler on which the events (onReply, onSessionEstablished etc) of this client will arrive
	 * @param uri of the server
	 * @param callbacks .The following methods must be implemented: onReply(Msg msg), onSessionEstablished(), 
	 * onSessionEvent(EventName session_event, EventReason reason), onMsgError()
	 * 
	 */

	public ClientSession(EventQueueHandler eventQHandler, URI uri, Callbacks callbacks) {
		this.eventQHandler = eventQHandler;
		this.callbacks = callbacks;
		if (!uri.getScheme().equals("rdma")) {
			LOG.fatal("mal formatted URI: " + uri);
		}

		final long id = Bridge.startSessionClient(uri.toString(), eventQHandler.getId());
		if (id == 0) {
			LOG.error("there was an error creating session");
		}

		if (LOG.isDebugEnabled()) {
			LOG.debug("id as recieved from C is " + id);
		}
		this.setId(id);

		this.eventQHandler.addEventable(this);
	}
	
	/** This method sends the request to server. 
	 * <p>
	 * The send is asynchronous, therefore even
	 * if the function returns, this does not mean that the msg reached the server or even was
	 * sent to the server.
	 * 
	 * @param msg
	 * @return boolean that indicated whether or not the queuing of the msg was successful. 
	 */

	public boolean sendRequest(Msg msg) {
		if (this.getIsClosing()) {
			LOG.warn("Trying to send message while session is closing");
			return false;
		}
		if (!Bridge.clientSendReq(this.getId(), msg.getId(), msg.getOut().position())) {
			LOG.error("there was an error sending the message");
			return false;
		}
		msg.setClientSession(this);
		//only if the send was successful the msg needs to be added to the "pending reply" list
		eventQHandler.addMsgInUse(msg);
		return true;
	}

	/** This method closes the ClientSession.
	 * <p>
	 * The method is asynchronous: the ClientSession will be closed only when
	 * it receives event SESSION_CLOSED
	 * 
	 * @return boolean that indicates whether there was a successful call to close of the Client object on C side
	 */
	public boolean close() {
		if (this.getIsClosing()) {
			LOG.warn(this.toString() + ": attempting to close client that is already closed or being closed");
			return false;
		}
		if (getId() == 0) {
			LOG.error(this.toString() + ": closing Session with empty id");
			return false;
		}
		setIsClosing(true);
		
		Bridge.closeSessionClient(getId());

		if (LOG.isDebugEnabled()) {
			LOG.debug(this.toString() + ": at the end of SessionClient:close()");
		}
		return true;
	}

	void onEvent(Event ev) {
		switch (ev.getEventType()) {

			case 0: // session error event
				if (LOG.isDebugEnabled()) {
					LOG.debug("received session event");
				}
				if (ev instanceof EventSession) {

					int errorType = ((EventSession) ev).getErrorType();
					int reason = ((EventSession) ev).getReason();
					EventName eventName = EventName.getEventByIndex(errorType);
					switch (eventName){
						case SESSION_CLOSED:
							this.setIsClosing(true); 
							// now we are officially done with this session and it can be deleted from the EQH
							eventQHandler.removeEventable(this);
							Bridge.deleteClient(this.getId());
							break;
						case SESSION_REJECT:
							//SESSION_CLOSED will arrive after SESSION_REJECT and then ClientSeesion will be deleted from EQH
							this.setIsClosing(true);   
							break;
						default:
							break;
					}
					callbacks.onSessionEvent(eventName, EventReason.getEventByIndex(reason));
				}
				break;

			case 2: // msg error
				LOG.error("received msg error event");
				EventMsgError evMsgErr;
				if (ev instanceof EventMsgError) {
					evMsgErr = (EventMsgError) ev;
					Msg msg = evMsgErr.getMsg();
					int reason = evMsgErr.getReason();
					callbacks.onMsgError(msg, EventReason.getEventByIndex(reason));
				} else {
					LOG.error("Event is not an instance of EventMsgError" + this.toString());
				}
				break;

			case 3: // session established
				if (LOG.isDebugEnabled()) {
					LOG.debug("received session established event");
				}
				callbacks.onSessionEstablished();
				break;

			case 5: // on reply
				if (LOG.isTraceEnabled()) {
					LOG.trace("received msg event at client" + this.toString());
				}
				EventNewMsg evNewMsg;
				if (ev instanceof EventNewMsg) {
					evNewMsg = (EventNewMsg) ev;
					Msg msg = evNewMsg.getMsg();
					callbacks.onReply(msg);
				} else {
					LOG.error("Event is not an instance of EventNewMsg" + this.toString());
				}

				break;

			default:
				LOG.error("received an unknown event " + ev.getEventType());
		}
	}
}
