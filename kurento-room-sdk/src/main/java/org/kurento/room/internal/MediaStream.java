


package org.kurento.room.internal;

//import java.util.HashSet;
//import java.util.Set;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
//import java.util.concurrent.TimeUnit;
//
//import org.kurento.client.Continuation;
//import org.kurento.client.ErrorEvent;
//import org.kurento.client.IceCandidate;
//import org.kurento.client.MediaElement;
import org.kurento.client.MediaPipeline;
//import org.kurento.client.MediaType;
//import org.kurento.client.SdpEndpoint;
//import org.kurento.client.internal.server.KurentoServerException;
//import org.kurento.room.api.MutedMediaType;
import org.kurento.room.endpoint.PublisherEndpoint;
//import org.kurento.room.endpoint.SdpType;
//import org.kurento.room.endpoint.SubscriberEndpoint;
//import org.kurento.room.exception.RoomException;
//import org.kurento.room.exception.RoomException.Code;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MediaStream {
	
	    public static final Logger log = LoggerFactory.getLogger(MediaStream.class);
	
		public String name;
		public Participant owner;
		
		public boolean web = false;
		public boolean dataChannels = false;
		
		public final MediaPipeline pipeline;
		
		public PublisherEndpoint publisher;
		public CountDownLatch endPointLatch = new CountDownLatch(1);

		public volatile boolean streaming = false;
		//public volatile boolean closed;
		
		public MediaStream(String name, Participant participant, MediaPipeline pipeline, boolean dataChannels, boolean web) {
			this.name = name;
			this.owner = participant;
			this.web = web;
			this.dataChannels = dataChannels;
			this.pipeline = pipeline;
			this.publisher = new PublisherEndpoint(web, dataChannels, participant, name, pipeline);		
		}
};