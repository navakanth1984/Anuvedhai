import * as Speech from 'expo-speech';
import * as Clipboard from 'expo-clipboard';
import * as Haptics from 'expo-haptics';
import { Alert } from 'react-native';
import { getTranscriptsCache, clearTranscriptsCache } from './OfflineCache';

export interface CommandResponse {
  type: 'NAVIGATE' | 'UTILITY' | 'SPEAK' | 'UNKNOWN';
  commandId: string;
  matchedText: string;
  feedbackSpeech: string;
  actionPayload?: any;
}

/**
 * Natural Language Parser (NLP) matching voice speech triggers to core actions.
 */
export async function parseVoiceCommand(
  rawInput: string,
  webViewRef: any,
  onRefresh: () => void,
  onClearCache: () => void
): Promise<CommandResponse> {
  const normalized = rawInput.toLowerCase().trim();

  // 1. HELP / VOICE UTILITY CHECKS
  if (normalized.includes('help') || normalized.includes('menu') || normalized.includes('what can i say')) {
    const feedback = "Hands free voice commands enabled. You can say: go to call translator, go to dialogue mode, reload web app, clear offline history, play recent translation, or copy active workspace link.";
    await speakText(feedback);
    return {
      type: 'UTILITY',
      commandId: 'help',
      matchedText: 'Describe voice helpers',
      feedbackSpeech: feedback
    };
  }

  // 2. NAVIGATING APP TABS
  if (normalized.includes('dialogue') || normalized.includes('chat') || normalized.includes('conversation')) {
    const script = `
      if (typeof window !== 'undefined') {
        const dialogBtn = document.querySelector('[testtag="integ_tab_voip"]') || document.querySelector('button:contains("VoIP")');
        // Let's attempt to trigger tab switches on the UI
        try {
          // Send web level postMessage or click navigation button
          window.dispatchEvent(new CustomEvent('change-tab-mode', { detail: 'dialogue' }));
        } catch(e) {}
      }
    `;
    // We also dispatch a postMessage back to app state to change tabs
    webViewRef.current?.injectJavaScript(`
      try {
        // Find matching menu buttons in AnuVedhai workspace structure
        const buttons = Array.from(document.querySelectorAll('button'));
        const targetBtn = buttons.find(b => b.textContent && (b.textContent.toLowerCase().includes('dialogue') || b.textContent.toLowerCase().includes('chat')));
        if (targetBtn) { targetBtn.click(); }
      } catch (err) {}
      true;
    `);
    
    // Send postMessage simulated packet
    webViewRef.current?.postMessage(JSON.stringify({ type: 'SWITCH_TAB', payload: { mode: 'dialogue' } }));

    const feedback = "Navigating to dialogue translation workspace.";
    await speakText(feedback);
    return {
      type: 'NAVIGATE',
      commandId: 'switch_dialogue',
      matchedText: 'Go to Dialogue Mode',
      feedbackSpeech: feedback
    };
  }

  if (normalized.includes('call') || normalized.includes('phone') || normalized.includes('voip') || normalized.includes('dialer')) {
    webViewRef.current?.injectJavaScript(`
      try {
        const buttons = Array.from(document.querySelectorAll('button'));
        const targetBtn = buttons.find(b => b.textContent && (b.textContent.toLowerCase().includes('call') || b.textContent.toLowerCase().includes('translator')));
        if (targetBtn) { targetBtn.click(); }
      } catch (err) {}
      true;
    `);

    webViewRef.current?.postMessage(JSON.stringify({ type: 'SWITCH_TAB', payload: { mode: 'call_translator' } }));

    const feedback = "Opening cellular and video call translator integration hub.";
    await speakText(feedback);
    return {
      type: 'NAVIGATE',
      commandId: 'switch_call',
      matchedText: 'Go to Call Translator',
      feedbackSpeech: feedback
    };
  }

  if (normalized.includes('mascot') || normalized.includes('buddy') || normalized.includes('assistant')) {
    webViewRef.current?.injectJavaScript(`
      try {
        const buttons = Array.from(document.querySelectorAll('button'));
        const targetBtn = buttons.find(b => b.textContent && b.textContent.toLowerCase().includes('mascot'));
        if (targetBtn) { targetBtn.click(); }
      } catch (err) {}
      true;
    `);

    webViewRef.current?.postMessage(JSON.stringify({ type: 'SWITCH_TAB', payload: { mode: 'mascot' } }));

    const feedback = "Switching to your virtual mascot translator companion.";
    await speakText(feedback);
    return {
      type: 'NAVIGATE',
      commandId: 'switch_mascot',
      matchedText: 'Go to Mascot Buddy',
      feedbackSpeech: feedback
    };
  }

  // 3. RELOADING & SYNC SENSORS
  if (normalized.includes('reload') || normalized.includes('refresh') || normalized.includes('restart app')) {
    onRefresh();
    const feedback = "Refreshing live translation workspace channels.";
    await speakText(feedback);
    return {
      type: 'UTILITY',
      commandId: 'reload_web',
      matchedText: 'Reload workspace web views',
      feedbackSpeech: feedback
    };
  }

  // 4. CLEARING HISTORY CACHE
  if (normalized.includes('clear offline') || normalized.includes('delete cache') || normalized.includes('wipe storage')) {
    onClearCache();
    const feedback = "Wiped all localized backup transcripts from offline memory.";
    await speakText(feedback);
    return {
      type: 'UTILITY',
      commandId: 'clear_offline',
      matchedText: 'Clear offline backup cache',
      feedbackSpeech: feedback
    };
  }

  // 5. COPY LINK
  if (normalized.includes('copy link') || normalized.includes('share link') || normalized.includes('get address')) {
    await Clipboard.setStringAsync('https://ais-pre-edle43pfhabgbasql6hdo6-319414224099.asia-east1.run.app');
    const feedback = "Workspace link copied successfully to system clipboard.";
    await speakText(feedback);
    return {
      type: 'UTILITY',
      commandId: 'copy_workspace',
      matchedText: 'Copy active translation URL link',
      feedbackSpeech: feedback
    };
  }

  // 6. VOCAL TRANSLATION FEEDBACK FOR BLIND OR DRIVING USERS
  if (normalized.includes('read recent') || normalized.includes('speak translation') || normalized.includes('play recent') || normalized.includes('speak recent')) {
    const cached = await getTranscriptsCache();
    if (cached && cached.length > 0) {
      const latest = cached[0];
      const readText = `Recent translation from ${latest.originalText} to: ${latest.translatedText}`;
      
      // Attempt to identify the language of the target translating engine for better synthesis accent
      let options: Speech.SpeechOptions = {};
      const targetLower = latest.targetLang.toLowerCase();
      if (targetLower.includes('hindi') || targetLower.includes('hin')) {
        options.language = 'hi-IN';
      } else if (targetLower.includes('spanish') || targetLower.includes('esp')) {
        options.language = 'es-ES';
      } else if (targetLower.includes('french') || targetLower.includes('fra')) {
        options.language = 'fr-FR';
      } else if (targetLower.includes('german')) {
        options.language = 'de-DE';
      } else {
        options.language = 'en-US';
      }

      await speakText(readText, options);
      return {
        type: 'SPEAK',
        commandId: 'speak_last_turn',
        matchedText: `Vocally speak: ${latest.translatedText}`,
        feedbackSpeech: readText
      };
    } else {
      const feedback = "Offline memory transcript bank is currently empty. Please speak or perform translations in the workspace first.";
      await speakText(feedback);
      return {
        type: 'SPEAK',
        commandId: 'speak_empty_error',
        matchedText: 'Play recent translator output',
        feedbackSpeech: feedback
      };
    }
  }

  // Fallback default
  const fallbackFeedback = `Unrecognized voice command: "${rawInput}". Say "help hands free" to vocalize matching triggers list.`;
  await speakText(fallbackFeedback);
  return {
    type: 'UNKNOWN',
    commandId: 'fallback',
    matchedText: rawInput,
    feedbackSpeech: fallbackFeedback
  };
}

/**
 * Executes high-performance speech synthesis to read back voice status reports safely.
 */
export async function speakText(text: string, options?: Speech.SpeechOptions) {
  try {
    // Interrupt any active speaking streams instantly
    await Speech.stop();
    await Speech.speak(text, {
      pitch: 1.05,
      rate: 0.95,
      ...options
    });
  } catch (err) {
    console.error('Failed synthesizing hands-free speech response:', err);
  }
}
