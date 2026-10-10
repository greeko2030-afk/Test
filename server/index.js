const express = require('express');
const http = require('http');
const { Server } = require('socket.io');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: {
    origin: '*',
    methods: ['GET', 'POST']
  }
});

const PORT = process.env.PORT || 3000;
const sourceSockets = new Map();
const clientSockets = new Map();

app.use(express.json());

app.get('/', (req, res) => {
  res.json({
    name: 'Test Relay Server',
    status: 'ok',
    roles: ['source', 'client'],
    endpoints: ['/health', '/']
  });
});

app.get('/health', (req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

function matchClientWithSource() {
  if (sourceSockets.size === 0 || clientSockets.size === 0) {
    return;
  }

  const [sourceId, sourceInfo] = sourceSockets.entries().next().value;
  const [clientId, clientInfo] = clientSockets.entries().next().value;

  if (!sourceInfo || !clientInfo) {
    return;
  }

  const sourceSocket = sourceInfo.socket;
  const clientSocket = clientInfo.socket;

  const payload = {
    sourceId,
    clientId,
    message: 'A relay connection is ready.'
  };

  sourceSocket.emit('relayRequest', {
    from: clientId,
    message: 'A client is asking for internet.'
  });

  clientSocket.emit('status', {
    type: 'relay-ready',
    message: 'Waiting for source approval...',
    sourceId
  });

  sourceSocket.emit('status', {
    type: 'relay-ready',
    message: 'A client is ready to connect.',
    clientId
  });

  console.log(`Matched source=${sourceId} with client=${clientId}`);
}

io.on('connection', (socket) => {
  console.log('Client connected:', socket.id);

  socket.on('registerSource', () => {
    sourceSockets.set(socket.id, { socket });
    socket.emit('status', {
      type: 'source-registered',
      message: 'Source registered successfully.'
    });
    console.log('Source registered:', socket.id);
    matchClientWithSource();
  });

  socket.on('registerClient', () => {
    clientSockets.set(socket.id, { socket });
    socket.emit('status', {
      type: 'client-registered',
      message: 'Client registered successfully. Waiting for source.'
    });
    console.log('Client registered:', socket.id);
    matchClientWithSource();
  });

  socket.on('acceptRelay', (payload = {}) => {
    const { sourceId, clientId } = payload;

    if (!sourceId || !clientId) {
      return;
    }

    const sourceSocket = io.sockets.sockets.get(sourceId);
    const clientSocket = io.sockets.sockets.get(clientId);

    if (sourceSocket && clientSocket) {
      sourceSocket.emit('status', {
        type: 'relay-accepted',
        message: 'Relay approved.',
        clientId
      });

      clientSocket.emit('status', {
        type: 'relay-accepted',
        message: 'Source approved the relay.',
        sourceId
      });

      sourceSocket.emit('connectedPeer', {
        peerId: clientId,
        type: 'client'
      });

      clientSocket.emit('connectedPeer', {
        peerId: sourceId,
        type: 'source'
      });
    }
  });

  socket.on('sendData', (payload = {}) => {
    const { target, data } = payload;

    if (!target) {
      socket.emit('status', {
        type: 'error',
        message: 'No target provided for sendData.'
      });
      return;
    }

    const targetSocket = io.sockets.sockets.get(target);
    if (targetSocket) {
      targetSocket.emit('data', {
        from: socket.id,
        payload: data || payload
      });
    } else {
      socket.emit('status', {
        type: 'error',
        message: 'Target not found.'
      });
    }
  });

  socket.on('ping', (payload, callback) => {
    if (typeof callback === 'function') {
      callback({ ok: true, timestamp: Date.now(), from: socket.id, payload });
    }
  });

  socket.on('disconnect', () => {
    sourceSockets.delete(socket.id);
    clientSockets.delete(socket.id);
    console.log('Client disconnected:', socket.id);
  });
});

server.listen(PORT, () => {
  console.log(`Test relay server running on http://localhost:${PORT}`);
});
