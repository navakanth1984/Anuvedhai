import * as Haptics from 'expo-haptics';
import { Alert, Share } from 'react-native';
import { appendToTranscriptsCache, saveTranscriptsCache, clearTranscriptsCache } from './OfflineCache';

/**
 * Handle incoming events from the client WebView container.
 * This bridge allows the web application running inside the React Native container
 * to invoke native platform APIs on Android and iOS.
 * 
 * In your Web frontend, call:
 * window.ReactNativeWebView?.postMessage(JSON.stringify({ type: 'EVENT_NAME', payload: {} }))
 */
export const handleNativeBridgeMessage = async (messageString: string) => {
  try {
    const event = JSON.parse(messageString);
    const { type, payload } = event;

    switch (type) {
      case 'TRANSLATION_SUCCESS':
        // Trigger a light tactile success haptic
        await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
        
        // Cache this new translation in AsyncStorage
        if (payload?.turn) {
          await appendToTranscriptsCache({
            id: payload.turn.id || Math.random().toString(),
            originalText: payload.turn.originalText || payload.turn.originalContent || '',
            translatedText: payload.turn.translatedText || '',
            sourceLang: payload.turn.sourceLang || payload.turn.sourceLanguageName || 'English',
            targetLang: payload.turn.targetLang || payload.turn.targetLanguageName || 'Hindi'
          });
        }
        break;

      case 'SYNC_CHAT_STREAMS':
        // Persist an entire batch or updated sequence of chat histories
        if (payload?.turns && Array.isArray(payload.turns)) {
          const mappedTurns = payload.turns.map((t: any) => ({
            id: t.id ? String(t.id) : Math.random().toString(),
            originalText: t.originalText || t.originalContent || '',
            translatedText: t.translatedText || '',
            sourceLang: t.sourceLang || t.sourceLanguageName || 'English',
            targetLang: t.targetLang || t.targetLanguageName || 'Hindi',
            timestamp: t.timestamp || new Date().toISOString()
          }));
          await saveTranscriptsCache(mappedTurns);
        }
        break;

      case 'CLEAR_CHAT_HISTORY':
        // Delete local cache
        await clearTranscriptsCache();
        break;

      case 'RECORDING_START':
        // Trigger a sensory feedback confirming voice record initiated
        await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
        break;

      case 'TTS_PLAY_COMPLETE':
        // Perform feedback on text-to-speech finishing
        await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
        break;

      case 'SHARE_TRANSLATION':
        // Invoke native share sheet on iOS/Android
        if (payload?.text) {
          await Share.share({
            message: payload.text,
            title: 'AnuVedhai Translation Output'
          });
        }
        break;

      case 'TRIGGER_NATIVE_ALERT':
        // Relay a native modal window
        Alert.alert(payload?.title || 'System Notification', payload?.message || '');
        break;

      default:
        console.log('Unrecognized bridge packet:', event);
    }
  } catch (error) {
    console.error('Failed to parse bridge communication telemetry:', error);
  }
};
