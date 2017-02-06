/*
 * @author Raquel Díaz González
 */

kurento_room.service('ServiceRoom', function () {

    var kurento;
    var kurentoRoom;
    var roomName;
    var userName;
    var localStream;

    this.getKurento = function () {
        return kurento;
    };

    this.getRoom = function () {
        return kurentoRoom;
    }

    this.getRoomName = function () {
        return roomName;
    };

    this.setKurento = function (value) {
        kurento = value;
    };

    this.setRoom = function (value) {
        kurentoRoom = value;
    }

    this.setRoomName = function (value) {
        roomName = value;
    };

    this.getLocalStream = function () {
        return localStream;
    };

    this.setLocalStream = function (value) {
        localStream = value;
    };

    this.getUserName = function () {
        return userName;
    };

    this.setUserName = function (value) {
        userName = value;
    };
});
