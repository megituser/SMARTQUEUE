'use client';

import { useEffect, useRef, useCallback } from 'react';
import wsService, { QueueUpdateCallback } from '@/lib/websocket';

export function useWebSocket() {
  useEffect(() => {
    wsService.connect();
    return () => { /* keep connection alive across navigations */ };
  }, []);

  const subscribeToQueue = useCallback((branchId: number, callback: QueueUpdateCallback) => {
    wsService.subscribeToQueue(branchId, callback);
    return () => wsService.unsubscribe(`/topic/queue/${branchId}`);
  }, []);

  const subscribeToToken = useCallback((tokenId: number, callback: QueueUpdateCallback) => {
    wsService.subscribeToToken(tokenId, callback);
    return () => wsService.unsubscribe(`/topic/token/${tokenId}`);
  }, []);

  const subscribeToCounter = useCallback((branchId: number, counterId: number, callback: QueueUpdateCallback) => {
    wsService.subscribeToCounter(branchId, counterId, callback);
    return () => wsService.unsubscribe(`/topic/counter/${branchId}/${counterId}`);
  }, []);

  const subscribeToTokenCalled = useCallback((branchId: number, callback: QueueUpdateCallback) => {
    wsService.subscribeToTokenCalled(branchId, callback);
    return () => wsService.unsubscribe(`/topic/queue/${branchId}/called`);
  }, []);

  return {
    subscribeToQueue,
    subscribeToToken,
    subscribeToCounter,
    subscribeToTokenCalled,
    isConnected: wsService.isConnected(),
  };
}
