/*
 * (C) Copyright 2016 Kurento (http://kurento.org/)
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
 *
 */
function MediaStream(_stream) {
    var that = this;
    this.kmsStream = _stream;
    this.name = _stream.getID();
    this.videoElement;
    this.thumbnailId;

    this.stream = function () {
        return that.kmsStream;
    }

    this.getName = function () {
        return that.name;
    }

    this.getVideoElement = function () {
        return that.videoElement;
    }

    this.getThumbId = function () {
        return that.thumbnailId;
    }

    function playVideo() {
        console.debug("new stream " + that.kmsStream.getGlobalID());
        that.thumbnailId = "video-" + that.kmsStream.getGlobalID();

        that.videoElement = document.createElement('div');
        that.videoElement.setAttribute("id", that.thumbnailId);
        that.videoElement.className = "video";

        var buttonVideo = document.createElement('button');
        buttonVideo.className = 'action btn btn--m btn--orange btn--fab mdi md-desktop-mac';
        //FIXME this won't work, Angular can't get to bind the directive ng-click nor lx-ripple
        buttonVideo.setAttribute("ng-click", "disconnectStream();$event.stopPropagation();");
        buttonVideo.setAttribute("lx-ripple", "");
        buttonVideo.style.position = "absolute";
        buttonVideo.style.left = "75%";
        buttonVideo.style.top = "60%";
        buttonVideo.style.zIndex = "100";
        that.videoElement.appendChild(buttonVideo);

        var speakerSpeakingVolumen = document.createElement('div');
        speakerSpeakingVolumen.setAttribute("id", "speaker" + that.thumbnailId);
        speakerSpeakingVolumen.className = 'btn--m btn--green btn--fab mdi md-volume-up blinking';
        speakerSpeakingVolumen.style.position = "absolute";
        speakerSpeakingVolumen.style.left = "3%";
        speakerSpeakingVolumen.style.top = "60%";
        speakerSpeakingVolumen.style.zIndex = "100";
        speakerSpeakingVolumen.style.display = "none";
        that.videoElement.appendChild(speakerSpeakingVolumen);

        document.getElementById("participants").appendChild(that.videoElement);
        console.debug("playThumbnail ", that.thumbnailId, " stream:" + that.name + " kmsStream:" + that.kmsStream.getGlobalID());
        that.kmsStream.playThumbnail(that.thumbnailId);
    }

    playVideo();
}

function AppParticipant(participant) {
    var streams = {};
    var name = participant.getID();
    // this.stream = stream;
    // this.videoElement;
    // this.thumbnailId;

    var that = this;

    console.debug("new participant " + name);
    var input_streams = participant.getStreams();
    for (var key in input_streams) {
        console.debug("part stream:" + key);
        if (key != null)
            streams[key] = new MediaStream(input_streams[key]);
    }

    this.isEmpty = function () {
        return Object.keys(streams).length===0;
    }

    this.addStream = function (stream) {
        var mediaStream = new MediaStream(stream);
        streams[stream.getID()] = mediaStream;
    }

    this.getStream = function (streamId) {
        return streams[streamId];
    }

    this.getStreams = function () {
        return streams;
    }

    this.getName = function () {
        return name;
    }

    this.setMain = function (streamId) {
        console.log("setMain part:" + name + " stream:" + streamId);

        var mainVideo = document.getElementById("main-video");
        var elementChildren = mainVideo.children;
        var oldVideoList = [];
        for (var i = 0; i < elementChildren.length; i++) {
            if(elementChildren[i].tagName === "VIDEO") {
                oldVideoList.push(elementChildren[i]);
            }
        }
        //var oldVideo = mainVideo.firstChild; // this is wrong, the first child is the comment of html

        streams[streamId].stream().playOnlyVideo("main-video", streams[streamId].getThumbId());

        streams[streamId].getVideoElement().className += " active-video";

        if (oldVideoList.length > 0) {
            //mainVideo.removeChild(oldVideo);
            for (var i = 0; i < oldVideoList.length; i++) {
                mainVideo.removeChild(oldVideoList[i]);
                $(oldVideoList[i]).remove();
            }
            oldVideoList = null;
        }
    }

    this.removeMain = function (streamId) {
        if (streamId && streams[streamId]) {
            $(streams[streamId].getVideoElement()).removeClass("active-video");
        }
    }

    this.removeStream = function (streamId) {
        var stream = streams[streamId];
        delete streams[streamId];
        if (stream.getVideoElement() !== undefined) {
            if (stream.getVideoElement().parentNode !== null) {
                stream.getVideoElement().parentNode.removeChild(stream.getVideoElement());
            }
        }
    }

    this.remove = function () {
        for (key in streams) {
            stream = streams[key];
            if (stream.getVideoElement() !== undefined) {
                if (stream.getVideoElement().parentNode !== null) {
                    stream.getVideoElement().parentNode.removeChild(stream.getVideoElement());
                }
            }
        }
        streams = {};
    }

    // function playVideo(streamId) {
    //     var stream = this.getStream(streamId);
    //     if (stream != undefinde && stream != null) {
    //         stream.playVideo();
    //     }
    // }
}

function Participants() {

    var mainParticipant;
    var mainStreamId;
    var localParticipant;
    var mirrorParticipant;
    var participants = {};
    var roomName;
    var that = this;
    var connected = true;
    var displayingRelogin = false;
    var mainSpeaker = true;

    this.isConnected = function () {
        return connected;
    }

    this.getRoomName = function () {
        console.log("room - getRoom " + roomName);
        roomName = room.name;
        return roomName;
    };

    this.getMainParticipant = function () {
        return mainParticipant;
    }

    this.getMainStream = function () {
        return mainParticipant.getStream(mainStreamId);
    }

    function updateVideoStyle() {
        var MAX_WIDTH = 14;
        //var numParticipants = Object.keys(participants).length;
        //var maxParticipantsWithMaxWidth = 98 / MAX_WIDTH;

        var numStreams = 0;
        for (key in participants) {
            var streams = participants[key].getStreams();
            if (streams != null)
                numStreams += Object.keys(streams).length;
        }
        var maxStreamsWithMaxWidth = 98 / MAX_WIDTH;

        if (numStreams > maxStreamsWithMaxWidth) {
            $('.video').css({
                "width": (98 / numStreams) + "%"
            });
        } else {
            $('.video').css({
                "width": MAX_WIDTH + "%"
            });
        }
    };

    function updateMainParticipant(participant, streamId) {
        console.debug("updateMainParticipant");
        if (mainParticipant) {
            console.debug("remove main part:" + mainParticipant.getName());
            mainParticipant.removeMain(mainStreamId);
        }
        mainParticipant = participant;
        mainStreamId = streamId;
        mainParticipant.setMain(mainStreamId);
    }

    this.addLocalParticipant = function (part, stream) {
        console.debug("addLocalParticipant");
        localParticipant = that.addParticipant(part);
        //var streamId = "webcam";
        //if (streamId in localParticipant.getStreams()) {
        //    updateMainParticipant(localParticipant, streamId);
        //}
    };

    this.getLocalParticipant = function () {
        return localParticipant;
    }

    this.addLocalMirror = function (stream) {
        mirrorParticipant = that.addParticipant(stream);
    };

    this.addParticipant = function (part) {
        console.debug("addParticipant")
        var participant = new AppParticipant(part);
        participants[participant.getName()] = participant;

        updateVideoStyle();

        var streams = participant.getStreams();
        //console.log("New AppParticipant streams: ", streams);
        for (key in streams) {
            $(streams[key].getVideoElement()).click(function (e) {
                updateMainParticipant(participant, key);
            });
        }

        var keys = Object.keys(streams);
        if (keys.length > 0) {
            updateMainParticipant(participant, keys[0]);
        }

        return participant;
    };

    function checkExit () {
        var numStream = 0;
        var activeStream;
        var part_keys = Object.keys(participants);
        for (var i=0; i<part_keys.length; i++) {
            if (participants[part_keys[i]] != undefined && participants[part_keys[i]] != null) {
                var allStreams = participants[part_keys[i]].getStreams();
                var stream_keys = Object.keys(allStreams);
                for(var k=0; k<stream_keys.length; k++) {
                    if(allStreams[stream_keys[k]] != undefined && allStreams[stream_keys[k]] != null) {
                        numStream ++;
                        activeStream = allStreams[stream_keys[k]];
                    }
                }
            }
        }
        if(numStream <= 1) {
            parent.parent.postMessage({message:"exit"}, "*");
        }
    }

    this.addStream = function (part, stream) {
        var participant = participants[part.getID()];
        if (participant == undefined || participant == null) {
            console.log("There is no participant for stream " + stream.getGlobalID());
            this.addParticipant(part);
            return;
        } else {
            participant.addStream(stream);
            var mediaStream = participant.getStream(stream.getID());
            $(mediaStream.getVideoElement()).click(function (e) {
                updateMainParticipant(participant, mediaStream.getName());
            });

            updateVideoStyle();

            updateMainParticipant(participant, stream.getID());
        }
    }

    this.removeParticipantByStream = function (stream) {
        this.removeParticipant(stream.getGlobalID());
    };

    this.disconnectParticipant = function (appParticipant) {
        //this.removeParticipant(appParticipant.getStream().getGlobalID());
        this.removeParticipant(appParticipant.getName());
    };

    this.disconnectStream = function (appParticipant, streamId) {
        var stream = appParticipant.getStream(streamId);
        this.removeStream(appParticipant.getName(), streamId);
    }

    this.removeStream = function (partId, streamId) {
        console.debug("removeStream " + partId + " " + streamId);
        var participant = participants[partId];
        var stream = participant.getStream(streamId);
        participant.removeStream(streamId);

        var tmp_keys = Object.keys(participant.getStreams());
        console.debug("Left streams:", tmp_keys);

        if (participant === mainParticipant && streamId === mainStreamId) {
            var keys = Object.keys(participant.getStreams());
            var candidate;
            var candidateStreamId;
            if (keys.length == 0) {
                var bMatched = false;
                for (key in participants) {
                    if (participants[key] !== mainParticipant &&
                        participants[key] !== localParticipant &&
                        participants[key] !== mirrorParticipant &&
                        !participants[key].isEmpty()) {
                        candidate = participants[key];
                        bMatched = true;
                        break;
                    }
                }
                if (bMatched == false) {
                    if (typeof (mirrorParticipant) !== undefined && mirrorParticipant !== null) {
                        candidate = mirrorParticipant;
                    } else {
                        candidate = localParticipant;
                    }
                }
                ids = Object.keys(candidate.getStreams());
                candidateStreamId = ids[0];
            } else {
                candidate = mainParticipant;
                candidateStreamId = ids[0];
            }

            updateVideoStyle();
            updateMainParticipant(candidate, candidateStreamId);
            //mainParticipant.setMain(mainStreamId);
        }

        // Send message to parent for exit
        checkExit();
    }

    this.removeParticipant = function (partId) {
        console.debug("removeParticipant");
        var participant = participants[partId];
        delete participants[partId];
        participant.remove();

        if (mirrorParticipant) {
            var otherLocal = null;
            if (participant === localParticipant) {
                otherLocal = mirrorParticipant;
            }
            if (participant === mirrorParticipant) {
                otherLocal = localParticipant;
            }
            if (otherLocal) {
                console.log("Removed local participant (or mirror) so removing the other local as well");
                //delete participants[otherLocal.getStream().getGlobalID()];
                delete participants[otherLocal.getName()];
                otherLocal.remove();
            }
        }

        //setting main
        if (mainParticipant && mainParticipant === participant) {
            var mainIsLocal = false;
            var candidate;
            var candidateStreamId;
            if (localParticipant) {
                if (participant !== localParticipant && participant !== mirrorParticipant) {
                    candidate = localParticipant;
                    mainIsLocal = true;
                } else {
                    localParticipant = null;
                    mirrorParticipant = null;
                }
            }
            if (!mainIsLocal) {
                var keys = Object.keys(participants);
                if (keys.length > 0) {
                    candidate = participants[keys[0]];
                } else {
                    candidate = null;
                }
            }
            if (candidate) {
                var keys = Object.keys(candidate.getStreams());
                if (keys.length > 0) {
                    candidateStreamId = keys[0];
                } else {
                    candidateStreamId = "webcam";
                }
                updateMainParticipant(candidate, candidateStreamId);
                //mainParticipant.setMain(mainStreamId);
                console.log("Main video from " + mainParticipant.getName() + " " + streamId);
            } else
                console.error("No media streams left to display");
        }

        updateVideoStyle();
    };

    //only called when leaving the room
    this.removeParticipants = function () {
        connected = false;
        for (var index in participants) {
            var participant = participants[index];
            participant.remove();
        }
    };

    this.getParticipants = function () {
        return participants;
    };

    this.enableMainSpeaker = function () {
        mainSpeaker = true;
    }

    this.disableMainSpeaker = function () {
        mainSpeaker = false;
    }

    this.clean = function () {
        mainParticipant = null;
        mainStreamId = null;
        localParticipant = null;
        mirrorParticipant = null;
        participants = {};
        roomName = null;
    }

    // Open the chat automatically when a message is received
    function autoOpenChat() {
        var selectedEffect = "slide";
        var options = { direction: "right" };
        if ($("#effect").is(':hidden')) {
            $("#content").animate({ width: '80%' }, 500);
            $("#effect").toggle(selectedEffect, options, 500);
        }
    };

    this.showMessage = function (room, user, message) {
        var ul = document.getElementsByClassName("list");

        var chatDiv = document.getElementById('chatDiv');
        var messages = $("#messages");
        var updateScroll = true;

        if (messages.outerHeight() - chatDiv.scrollTop > chatDiv.offsetHeight) {
            updateScroll = false;
        }
        console.log(localParticipant)
        var localUser = localParticipant.thumbnailId.replace("_webcam", "").replace("video-", "");
        if (room === roomName && user === localUser) { //me

            var li = document.createElement('li');
            li.className = "list-row list-row--has-primary list-row--has-separator";
            var div1 = document.createElement("div1");
            div1.className = "list-secondary-tile";
            var img = document.createElement("img");
            img.className = "list-primary-tile__img";
            img.setAttribute("src", "http://ui.lumapps.com/images/placeholder/2-square.jpg");
            var div2 = document.createElement('div');
            div2.className = "list-content-tile list-content-tile--two-lines";
            var strong = document.createElement('strong');
            strong.innerHTML = user;
            var span = document.createElement('span');
            span.innerHTML = message;
            div2.appendChild(strong);
            div2.appendChild(span);
            div1.appendChild(img);
            li.appendChild(div1);
            li.appendChild(div2);
            ul[0].appendChild(li);

            //               <li class="list-row list-row--has-primary list-row--has-separator">
            //                        <div class="list-secondary-tile">
            //                            <img class="list-primary-tile__img" src="http://ui.lumapps.com/images/placeholder/2-square.jpg">
            //                        </div>
            //
            //                        <div class="list-content-tile list-content-tile--two-lines">
            //                            <strong>User 1</strong>
            //                            <span>.............................</span>
            //                        </div>
            //                    </li>


        } else {//others

            var li = document.createElement('li');
            li.className = "list-row list-row--has-primary list-row--has-separator";
            var div1 = document.createElement("div1");
            div1.className = "list-primary-tile";
            var img = document.createElement("img");
            img.className = "list-primary-tile__img";
            img.setAttribute("src", "http://ui.lumapps.com/images/placeholder/1-square.jpg");
            var div2 = document.createElement('div');
            div2.className = "list-content-tile list-content-tile--two-lines";
            var strong = document.createElement('strong');
            strong.innerHTML = user;
            var span = document.createElement('span');
            span.innerHTML = message;
            div2.appendChild(strong);
            div2.appendChild(span);
            div1.appendChild(img);
            li.appendChild(div1);
            li.appendChild(div2);
            ul[0].appendChild(li);
            autoOpenChat();

            //                 <li class="list-row list-row--has-primary list-row--has-separator">
            //                        <div class="list-primary-tile">
            //                            <img class="list-primary-tile__img" src="http://ui.lumapps.com/images/placeholder/1-square.jpg">
            //                        </div>
            //
            //                        <div class="list-content-tile list-content-tile--two-lines">
            //                            <strong>User 2</strong>
            //                            <span>.............................</span>
            //                        </div>
            //                    </li>
        }

        if (updateScroll) {
            chatDiv.scrollTop = messages.outerHeight();
        }
    };

    this.showError = function ($window, LxNotificationService, e) {
        if (displayingRelogin) {
            console.warn('Already displaying an alert that leads to relogin');
            return false;
        }
        displayingRelogin = true;
        that.removeParticipants();
        LxNotificationService.alert('Error!', e.error.message, 'Reconnect', function (answer) {
            displayingRelogin = false;
            $window.location.href = '/';
        });
    };

    this.forceClose = function ($window, LxNotificationService, msg) {
        if (displayingRelogin) {
            console.warn('Already displaying an alert that leads to relogin');
            return false;
        }
        displayingRelogin = true;
        that.removeParticipants();
        LxNotificationService.alert('Warning!', msg, 'Reload', function (answer) {
            displayingRelogin = false;
            $window.location.href = '/';
        });
    };

    this.alertMediaError = function ($window, LxNotificationService, msg, callback) {
        if (displayingRelogin) {
            console.warn('Already displaying an alert that leads to relogin');
            return false;
        }
        LxNotificationService.confirm('Warning!', 'Server media error: ' + msg
            + ". Please reconnect.", { cancel: 'Disagree', ok: 'Agree' },
            function (answer) {
                console.log("User agrees upon media error: " + answer);
                if (answer) {
                    that.removeParticipants();
                    $window.location.href = '/';
                }
                if (typeof callback === "function") {
                    callback(answer);
                }
            });
    };

    this.streamSpeaking = function (participantId) {
        var participant = participants[participantId.participantId];
        if (participant != undefined) {
            var stream = participant.getStream("webcam");
            if (stream == null) {
                var keys = Object.keys(mainParticipant.getStreams());
                stream = participant.getStream(keys[0]);
            }
            document.getElementById("speaker" + stream.getThumbId()).style.display = '';
        }
    }

    this.streamStoppedSpeaking = function (participantId) {
        var participant = participants[participantId.participantId];
        if (participant != undefined) {
            var stream = participant.getStream("webcam");
            if (stream == null) {
                var keys = Object.keys(mainParticipant.getStreams());
                stream = participant.getStream(keys[0]);
            }
            document.getElementById("speaker" + stream.getThumbId()).style.display = "none";
        }
    }

    this.updateMainSpeaker = function (participantId) {
        var participant = participants[participantId.participantId];
        if (participant != undefined) {
            if (mainSpeaker) {
                var stream = participant.getStream("desktop");
                if (stream == null) {
                    var keys = Object.keys(mainParticipant.getStreams());
                    stream = participant.getStream(keys[0]);
                }
                updateMainParticipant(participant, stream.getName());
            }
        }
    }
}