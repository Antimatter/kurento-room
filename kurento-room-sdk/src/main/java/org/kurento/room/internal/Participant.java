/*
 * (C) Copyright 2014 Kurento (http://kurento.org/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kurento.room.internal;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.kurento.client.Continuation;
import org.kurento.client.ErrorEvent;
import org.kurento.client.IceCandidate;
import org.kurento.client.MediaElement;
import org.kurento.client.MediaPipeline;
import org.kurento.client.MediaType;
import org.kurento.client.SdpEndpoint;
import org.kurento.client.internal.server.KurentoServerException;
import org.kurento.room.api.MutedMediaType;
import org.kurento.room.endpoint.PublisherEndpoint;
import org.kurento.room.endpoint.SdpType;
import org.kurento.room.endpoint.SubscriberEndpoint;
import org.kurento.room.exception.RoomException;
import org.kurento.room.exception.RoomException.Code;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//public class MediaStream {
//	
//	    public static final Logger log = LoggerFactory.getLogger(MediaStream.class);
//	
//		public String name;
//		//private Participant participant;
//		//private Room room;
//		
//		public boolean web = false;
//		public boolean dataChannels = false;
//		
//		//private final MediaPipeline pipeline;
//		
//		public PublisherEndpoint publisher;
//		public CountDownLatch endPointLatch = new CountDownLatch(1);
//
//		public volatile boolean streaming = false;
//		//public volatile boolean closed;
//		
//		public MediaStream(String name, Participant participant, MediaPipeline pipeline, boolean dataChannels, boolean web) {
//			this.name = name;
//			//this.participant = participant;
//			//this.room = room;
//			this.web = web;
//			this.dataChannels = dataChannels;
//			//this.pipeline = pipeline;
//			this.publisher = new PublisherEndpoint(web, dataChannels, participant, name, pipeline);		
//		}
//};

/**
 * @author Ivan Gracia (izanmail@gmail.com)
 * @author Micael Gallego (micael.gallego@gmail.com)
 * @author Radu Tom Vlad (rvlad@naevatec.com)
 * @since 1.0.0
 */
public class Participant {

  private static final Logger log = LoggerFactory.getLogger(Participant.class);

  private String id;
  private String name;
  private boolean web = false;
//  private boolean dataChannels = false;

  private final Room room;

  private final MediaPipeline pipeline;
  
  private final ConcurrentMap<String, MediaStream> mediaStreams = new ConcurrentHashMap<String, MediaStream>();


//  private PublisherEndpoint publisher;
//  private CountDownLatch endPointLatch = new CountDownLatch(1);
//
  private final ConcurrentMap<String, SubscriberEndpoint> subscribers = new ConcurrentHashMap<String, SubscriberEndpoint>();
//
//  private volatile boolean streaming = false;
  private volatile boolean closed;

  public Participant(String id, String name, String streamId, Room room, MediaPipeline pipeline,
      boolean dataChannels, boolean web) {
    this.id = id;
    this.name = name;
    this.web = web;
//    this.dataChannels = dataChannels;
    this.pipeline = pipeline;
    this.room = room;
//    this.publisher = new PublisherEndpoint(web, dataChannels, this, name, pipeline);
    
    // Create a new stream
//    MediaStream stream = new MediaStream(streamId, this, this.pipeline, dataChannels, web);
//    MediaStream existingStream = this.mediaStreams.putIfAbsent(streamId, stream);
//    if (existingStream != null) {
//      log.trace("PARTICIPANT {}: Already exists a stream {}", this.name, streamId);
//    } else {
//      log.debug("PARTICIPANT {}: New stream {}", this.name, streamId);
//    }
    createStream(streamId, dataChannels, web);

    // Add subscribers for the new stream.
    for (Participant other : room.getParticipants()) {
      if (!other.getName().equals(this.name)) {
    	  for(MediaStream stream : other.getStreams()) {
    		  getNewOrExistingSubscriber(other.getName(), stream.name);
    	  }
      }
    }
  }
  
  private MediaStream createStream(String streamId, boolean dataChannels, boolean web) {
	  MediaStream stream = new MediaStream(streamId, this, this.pipeline, dataChannels, web);
	  MediaStream existingStream = this.mediaStreams.putIfAbsent(streamId, stream);
	  if (existingStream != null) {
		  log.trace("PARTICIPANT {}: Already exists a stream {}", this.name, streamId);
	  } else {
		  log.debug("PARTICIPANT {}: New stream {}", this.name, streamId);
	  }
	  return stream;
  }
  
  public Set<MediaStream> getStreams() {
	  Set<MediaStream> streams = new HashSet<MediaStream>(this.mediaStreams.values()); 
	  return streams;
  }
  
  public MediaStream getStream(String streamId) {
	  MediaStream stream = this.mediaStreams.get(streamId);
	  if(stream == null) {
		  log.trace("PARTICIPANT {} the stream {} is not exist!");
	  }
	  return stream;
  }

  public void createPublishingEndpoint(String streamId) {
	MediaStream stream = this.getStream(streamId);
	if(stream == null) {
		stream = this.createStream(streamId, false, this.web);
	}
    stream.publisher.createEndpoint(stream.endPointLatch);
    if (getPublisher(streamId).getEndpoint() == null) {
      throw new RoomException(Code.MEDIA_ENDPOINT_ERROR_CODE, "Unable to create publisher endpoint");
    }
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void shapePublisherMedia(String streamId, MediaElement element, MediaType type) {
	MediaStream stream = this.getStream(streamId);
    if (type == null) {
      stream.publisher.apply(element);
    } else {
      stream.publisher.apply(element, type);
    }
  }

  public PublisherEndpoint getPublisher(String streamId) {
	MediaStream stream = this.getStream(streamId);
    try {
      if (!stream.endPointLatch.await(Room.ASYNC_LATCH_TIMEOUT, TimeUnit.SECONDS)) {
        throw new RoomException(Code.MEDIA_ENDPOINT_ERROR_CODE,
            "Timeout reached while waiting for publisher endpoint to be ready");
      }
    } catch (InterruptedException e) {
      throw new RoomException(Code.MEDIA_ENDPOINT_ERROR_CODE,
          "Interrupted while waiting for publisher endpoint to be ready: " + e.getMessage());
    }
    return stream.publisher;
  }

  public Room getRoom() {
    return this.room;
  }

  public MediaPipeline getPipeline() {
    return pipeline;
  }

  public boolean isClosed() {
    return this.closed;
  }

  public boolean isStreaming(String streamId) {
	MediaStream stream = this.getStream(streamId);
    return stream.streaming;
  }

  public boolean isSubscribed() {
    for (SubscriberEndpoint se : this.subscribers.values()) {
      if (se.isConnectedToPublisher()) {
        return true;
      }
    }
    return false;
  }

  public Set<String> getConnectedSubscribedEndpoints() {
    Set<String> subscribedToSet = new HashSet<String>();
    for (SubscriberEndpoint se : this.subscribers.values()) {
      if (se.isConnectedToPublisher()) {
        subscribedToSet.add(se.getEndpointName());
      }
    }
    return subscribedToSet;
  }

  public String preparePublishConnection(String streamId) {
	MediaStream stream = this.getStream(streamId);
    log.info("USER {}: Request to publish video {} in room {} by "
        + "initiating connection from server", this.name, stream.name, this.room.getName());

    String sdpOffer = this.getPublisher(streamId).preparePublishConnection();

    log.trace("USER {}: Publishing stream {} SdpOffer is {}", this.name, stream.name, sdpOffer);
    log.info("USER {}: Stream {} generated Sdp offer for publishing in room {}", this.name, stream.name,
        this.room.getName());
    return sdpOffer;
  }

  public String publishToRoom(String streamId, SdpType sdpType, String sdpString, boolean doLoopback,
      MediaElement loopbackAlternativeSrc, MediaType loopbackConnectionType) {
	MediaStream stream = this.getStream(streamId);
    log.info("USER {}: Request to publish video {} in room {} (sdp type {})", this.name, stream.name,
        this.room.getName(), sdpType);
    log.trace("USER {}: Stream publishing Sdp ({}) is {}", this.name, stream.name, sdpType, sdpString);

    String sdpResponse = this.getPublisher(streamId).publish(sdpType, sdpString, doLoopback,
        loopbackAlternativeSrc, loopbackConnectionType);
    stream.streaming = true;

    log.trace("USER {}: Stream {} publishing Sdp ({}) is {}", this.name, stream.name, sdpType, sdpResponse);
    log.info("USER {}: Is now publishing video {} in room {}", this.name, stream.name, this.room.getName());

    return sdpResponse;
  }

  public void unpublishMedia(String streamId) {
	MediaStream stream = this.getStream(streamId);
    log.debug("PARTICIPANT {}: unpublishing media stream {} from room {}", this.name, stream.name,
        this.room.getName());
    releasePublisherEndpoint(streamId);
    stream.publisher = new PublisherEndpoint(web, stream.dataChannels, this, streamId, pipeline);
    log.debug("PARTICIPANT {}: released publisher endpoint and left it "
        + "initialized (ready for future streaming)", this.name);
  }

  public String receiveMediaFrom(Participant sender, String streamId, String sdpOffer) {
    final String senderName = sender.getName();
    MediaStream senderStream = sender.getStream(streamId);

    log.info("USER {}: Request to receive media from {} in room {}", this.name, senderName,
        this.room.getName());
    log.trace("USER {}: SdpOffer for {} is {}", this.name, senderName, sdpOffer);

    if (senderName.equals(this.name)) {
      log.warn("PARTICIPANT {}: trying to configure loopback by subscribing", this.name);
      throw new RoomException(Code.USER_NOT_STREAMING_ERROR_CODE,
          "Can loopback only when publishing media");
    }

    if (sender.getPublisher(streamId) == null) {
      log.warn("PARTICIPANT {}: Trying to connect to a user without " + "a publishing endpoint",
          this.name);
      return null;
    }

    log.debug("PARTICIPANT {}: Creating a subscriber endpoint to user {}", this.name, senderName);

    SubscriberEndpoint subscriber = getNewOrExistingSubscriber(senderName, streamId);

    try {
      CountDownLatch subscriberLatch = new CountDownLatch(1);
      SdpEndpoint oldMediaEndpoint = subscriber.createEndpoint(subscriberLatch);
      try {
        if (!subscriberLatch.await(Room.ASYNC_LATCH_TIMEOUT, TimeUnit.SECONDS)) {
          throw new RoomException(Code.MEDIA_ENDPOINT_ERROR_CODE,
              "Timeout reached when creating subscriber endpoint");
        }
      } catch (InterruptedException e) {
        throw new RoomException(Code.MEDIA_ENDPOINT_ERROR_CODE,
            "Interrupted when creating subscriber endpoint: " + e.getMessage());
      }
      if (oldMediaEndpoint != null) {
        log.warn("PARTICIPANT {}: Two threads are trying to create at "
            + "the same time a subscriber endpoint for user {}", this.name, senderName);
        return null;
      }
      if (subscriber.getEndpoint() == null) {
        throw new RoomException(Code.MEDIA_ENDPOINT_ERROR_CODE,
            "Unable to create subscriber endpoint");
      }
    } catch (RoomException e) {
      this.subscribers.remove(senderName+'_'+streamId);
      throw e;
    }

    log.debug("PARTICIPANT {}: Created subscriber endpoint for user {}", this.name, senderName);
    try {
      String sdpAnswer = subscriber.subscribe(sdpOffer, sender.getPublisher(streamId));
      log.trace("USER {}: Subscribing SdpAnswer is {}", this.name, sdpAnswer);
      log.info("USER {}: Is now receiving video from {} in room {}", this.name, senderName,
          this.room.getName());
      return sdpAnswer;
    } catch (KurentoServerException e) {
      // TODO Check object status when KurentoClient sets this info in the
      // object
      if (e.getCode() == 40101) {
        log.warn("Publisher endpoint was already released when trying "
            + "to connect a subscriber endpoint to it", e);
      } else {
        log.error("Exception connecting subscriber endpoint " + "to publisher endpoint", e);
      }
      this.subscribers.remove(senderName+'_'+streamId);
      releaseSubscriberEndpoint(senderName, subscriber);
    }
    return null;
  }

  public void cancelReceivingMedia(String senderName, String streamId) {
    log.debug("PARTICIPANT {}: cancel receiving media from {}_{}", this.name, senderName, streamId);
    SubscriberEndpoint subscriberEndpoint = subscribers.remove(senderName+'_'+streamId);
    if (subscriberEndpoint == null || subscriberEndpoint.getEndpoint() == null) {
      log.warn("PARTICIPANT {}: Trying to cancel receiving video from user {}. "
          + "But there is no such subscriber endpoint.", this.name, senderName);
    } else {
      log.debug("PARTICIPANT {}: Cancel subscriber endpoint linked to user {}", this.name,
          senderName);

      releaseSubscriberEndpoint(senderName, subscriberEndpoint);
    }
  }

  public void mutePublishedMedia(String streamId, MutedMediaType muteType) {
    if (muteType == null) {
      throw new RoomException(Code.MEDIA_MUTE_ERROR_CODE, "Mute type cannot be null");
    }
    this.getPublisher(streamId).mute(muteType);
  }

  public void unmutePublishedMedia(String streamId) {
    if (this.getPublisher(streamId).getMuteType() == null) {
      log.warn("PARTICIPANT {}: Trying to unmute published media. " + "But media is not muted.",
          this.name);
    } else {
      this.getPublisher(streamId).unmute();
    }
  }

  public void muteSubscribedMedia(Participant sender, String streamId, MutedMediaType muteType) {
    if (muteType == null) {
      throw new RoomException(Code.MEDIA_MUTE_ERROR_CODE, "Mute type cannot be null");
    }
    String senderName = sender.getName();
    SubscriberEndpoint subscriberEndpoint = subscribers.get(senderName+'_'+streamId);
    if (subscriberEndpoint == null || subscriberEndpoint.getEndpoint() == null) {
      log.warn("PARTICIPANT {}: Trying to mute incoming media from user {}. "
          + "But there is no such subscriber endpoint.", this.name, senderName);
    } else {
      log.debug("PARTICIPANT {}: Mute subscriber endpoint linked to user {}", this.name, senderName);
      subscriberEndpoint.mute(muteType);
    }
  }

  public void unmuteSubscribedMedia(Participant sender, String streamId) {
    String senderName = sender.getName();
    SubscriberEndpoint subscriberEndpoint = subscribers.get(senderName+'_'+streamId);
    if (subscriberEndpoint == null || subscriberEndpoint.getEndpoint() == null) {
      log.warn("PARTICIPANT {}: Trying to unmute incoming media from user {}. "
          + "But there is no such subscriber endpoint.", this.name, senderName);
    } else {
      if (subscriberEndpoint.getMuteType() == null) {
        log.warn("PARTICIPANT {}: Trying to unmute incoming media from user {}. "
            + "But media is not muted.", this.name, senderName);
      } else {
        log.debug("PARTICIPANT {}: Unmute subscriber endpoint linked to user {}", this.name,
            senderName);
        subscriberEndpoint.unmute();
      }
    }
  }

  public void close() {
    log.debug("PARTICIPANT {}: Closing user", this.name);
    if (isClosed()) {
      log.warn("PARTICIPANT {}: Already closed", this.name);
      return;
    }
    this.closed = true;
    for (String remoteParticipantName : subscribers.keySet()) {
      SubscriberEndpoint subscriber = this.subscribers.get(remoteParticipantName);
      if (subscriber != null && subscriber.getEndpoint() != null) {
        releaseSubscriberEndpoint(remoteParticipantName, subscriber);
        log.debug("PARTICIPANT {}: Released subscriber endpoint to {}", this.name,
            remoteParticipantName);
      } else {
        log.warn("PARTICIPANT {}: Trying to close subscriber endpoint to {}. "
            + "But the endpoint was never instantiated.", this.name, remoteParticipantName);
      }
    }
    
    // Release all streams
    for (String streamId : mediaStreams.keySet()) {
    	releasePublisherEndpoint(streamId);
    }
  }

  /**
   * Returns a {@link SubscriberEndpoint} for the given username. The endpoint is created if not
   * found.
   *
   * @param remoteName
   *          name of another user
   * @return the endpoint instance
   */
  public SubscriberEndpoint getNewOrExistingSubscriber(String remoteName, String streamId) {
	  Participant sender = this.room.getParticipantByName(remoteName);
	  MediaStream stream = sender.getStream(streamId);
	  String remoteEpName = stream.publisher.getEndpointName();
	  SubscriberEndpoint sendingEndpoint = new SubscriberEndpoint(web, this, remoteName, remoteEpName, pipeline);
	  SubscriberEndpoint existingSendingEndpoint = this.subscribers.putIfAbsent(remoteName+'_'+remoteEpName,
			  sendingEndpoint);
	  if (existingSendingEndpoint != null) {
		  sendingEndpoint = existingSendingEndpoint;
		  log.trace("PARTICIPANT {}: Already exists a subscriber endpoint to user {}", this.name,
				  remoteEpName);
	  } else {
		  log.debug("PARTICIPANT {}: New subscriber endpoint to user {} {}", this.name, remoteEpName);
	  }
	  return sendingEndpoint;
  }
  
  public Set<SubscriberEndpoint> getNewOrExistingSubscribers(String remoteName) {
	  Participant sender = this.room.getParticipantByName(remoteName);
	  Set<MediaStream> streams = sender.getStreams();
	  Set<SubscriberEndpoint> sendingEndpointSet = new HashSet<SubscriberEndpoint>();
	  
	  for(MediaStream stream: streams) {
		  String remoteEpName = stream.publisher.getEndpointName();
		  SubscriberEndpoint sendingEndpoint = new SubscriberEndpoint(web, this, remoteName, remoteEpName, pipeline);
		  SubscriberEndpoint existingSendingEndpoint = this.subscribers.putIfAbsent(remoteName+'_'+remoteEpName,
				  sendingEndpoint);
		  if (existingSendingEndpoint != null) {
			  sendingEndpoint = existingSendingEndpoint;
			  log.trace("PARTICIPANT {}: Already exists a subscriber endpoint to user {}", this.name,
					  remoteEpName);
		  } else {
			  log.debug("PARTICIPANT {}: New subscriber endpoint to user {}", this.name, remoteEpName);
		  }
		  sendingEndpointSet.add(sendingEndpoint);
	  }
	  return sendingEndpointSet;
  }

  public void addIceCandidate(String endpointName, String streamId, IceCandidate iceCandidate) {
	  MediaStream stream = this.getStream(streamId);  //this.mediaStreams.get(endpointName);
	  
	  
	  
	  if (stream != null && this.name.equals(endpointName)) {
		  log.debug("PARTICIPANT {}: publisher add ice endpointName:{} streamId:{} ++++++++++++++++++++++++++++", 
				  this.name, endpointName, streamId);
		  stream.publisher.addIceCandidate(iceCandidate);
	  } else {
		  log.debug("PARTICIPANT {}: subscriber ice endpointName:{} streamId:{} ----------------------------", 
				  this.name, endpointName, streamId);
		  String remoteName = endpointName;		  
		  this.getNewOrExistingSubscriber(remoteName, streamId).addIceCandidate(iceCandidate);
	  }
  }

  public void sendIceCandidate(String remoteName, String streamId, IceCandidate candidate) {
    room.sendIceCandidate(id, remoteName, streamId, candidate);
  }

  public void sendMediaError(ErrorEvent event) {
    String desc = event.getType() + ": " + event.getDescription() + "(errCode="
        + event.getErrorCode() + ")";
    log.warn("PARTICIPANT {}: Media error encountered: {}", name, desc);
    room.sendMediaError(id, desc);
  }

  private void releasePublisherEndpoint(String streamId) {
	MediaStream stream = this.getStream(streamId);
    if (stream.publisher != null && stream.publisher.getEndpoint() != null) {
      stream.streaming = false;
      stream.publisher.unregisterErrorListeners();
      for (MediaElement el : stream.publisher.getMediaElements()) {
        releaseElement(name, el);
      }
      releaseElement(name, stream.publisher.getEndpoint());
      stream.publisher = null;
    } else {
      log.warn("PARTICIPANT {}: Trying to release publisher endpoint but is null", name);
    }
  }

  private void releaseSubscriberEndpoint(String senderName, SubscriberEndpoint subscriber) {
    if (subscriber != null) {
      subscriber.unregisterErrorListeners();
      releaseElement(senderName, subscriber.getEndpoint());
    } else {
      log.warn("PARTICIPANT {}: Trying to release subscriber endpoint for '{}' but is null", name,
          senderName);
    }
  }

  private void releaseElement(final String senderName, final MediaElement element) {
    final String eid = element.getId();
    try {
      element.release(new Continuation<Void>() {
        @Override
        public void onSuccess(Void result) throws Exception {
          log.debug("PARTICIPANT {}: Released successfully media element #{} for {}",
              Participant.this.name, eid, senderName);
        }

        @Override
        public void onError(Throwable cause) throws Exception {
          log.warn("PARTICIPANT {}: Could not release media element #{} for {}",
              Participant.this.name, eid, senderName, cause);
        }
      });
    } catch (Exception e) {
      log.error("PARTICIPANT {}: Error calling release on elem #{} for {}", name, eid, senderName,
          e);
    }
  }

  @Override
  public String toString() {
    return "[User: " + name + "]";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + (id == null ? 0 : id.hashCode());
    result = prime * result + (name == null ? 0 : name.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof Participant)) {
      return false;
    }
    Participant other = (Participant) obj;
    if (id == null) {
      if (other.id != null) {
        return false;
      }
    } else if (!id.equals(other.id)) {
      return false;
    }
    if (name == null) {
      if (other.name != null) {
        return false;
      }
    } else if (!name.equals(other.name)) {
      return false;
    }
    return true;
  }
}
