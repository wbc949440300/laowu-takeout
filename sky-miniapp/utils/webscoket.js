import { Stomp } from './stomp'
import store from './../store'

export default function initWebScoket(params){
	
	var socketOpen = false;
	var socketMsgQueue = [];	
	var ws = {
	  send: sendSocketMessage,
	  onopen: null,
	  onmessage: null
	}
	
	function sendSocketMessage(msg) {
		 if (socketOpen) {
			uni.sendSocketMessage({ data: msg });
			console.log('淇℃伅宸插彂閫侊細' + msg);
		  } else {
			socketMsgQueue.push(msg);
		  }
	}
	
	uni.connectSocket({
	  url: 'ws://localhost:8080/ws/1'
	});
	
	uni.onSocketOpen(function (res) {
	  socketOpen = true;
	  console.log('WebSocket连接已打开');
	  for (var i = 0; i < socketMsgQueue.length; i++) {
	    sendSocketMessage(socketMsgQueue[i]);
	  }
	  socketMsgQueue = [];
	  ws.onopen && ws.onopen()
	});
	
	uni.onSocketError(function (res) {
	  console.log('WebSocket连接已打开');
	});
	
	uni.onSocketMessage(function (res) {
	  console.log('鏀跺埌鏈嶅姟鍣ㄥ唴瀹癸細', res);
	  ws.onmessage && ws.onmessage(res)
	});
	
	uni.onSocketClose(function (res) {
	  console.log('WebSocket连接已打开');
	});
	
	var client = Stomp.over(ws)
	
	var destination = `/exchange/micro_app_exchange/${params.tableId}`
	client.connect('guest', 'guest', sessionId => {
	  console.log('sessionId', sessionId)
	  client.subscribe(destination, (body, headers) => {
		console.log('From MQ:', JSON.parse(body.body))
		store.commit('initdishListMut', JSON.parse(body.body))
	  })
	})
}