import React, { useState, useEffect, useRef } from 'react';
import {
  SafeAreaView,
  StyleSheet,
  View,
  ActivityIndicator,
  Text,
  TouchableOpacity,
  BackHandler,
  Platform,
  Alert,
  StatusBar,
  FlatList,
  TextInput
} from 'react-native';
import { WebView, WebViewNavigation } from 'react-native-webview';
import * as Network from 'expo-network';
import * as Haptics from 'expo-haptics';
import * as Clipboard from 'expo-clipboard';
import * as Battery from 'expo-battery';
import * as Speech from 'expo-speech';
import { NativeHeader } from './src/components/NativeHeader';
import { handleNativeBridgeMessage } from './src/utils/BridgeHandler';
import { getTranscriptsCache, clearTranscriptsCache, CachedTranslation } from './src/utils/OfflineCache';
import { parseVoiceCommand, speakText } from './src/utils/VoiceCommandNLP';
import { LANGUAGE_COURSES, LanguageCourse, CoursePhrase } from './src/utils/LanguageCourseData';

// The production web application deployments of AnuVedhai
const WEB_APP_URL = 'https://ais-pre-edle43pfhabgbasql6hdo6-319414224099.asia-east1.run.app';

export default function App() {
  const webViewRef = useRef<WebView>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [canGoBack, setCanGoBack] = useState(false);
  const [currentUrl, setCurrentUrl] = useState(WEB_APP_URL);
  const [isConnected, setIsConnected] = useState<boolean | null>(true);
  const [cachedTranscripts, setCachedTranscripts] = useState<CachedTranslation[]>([]);

  // Hands-free Voice Command interface states
  const [voiceCommandMode, setVoiceCommandMode] = useState(false);
  const [voiceInputText, setVoiceInputText] = useState('');
  const [nlpFeedbackMsg, setNlpFeedbackMsg] = useState('Waiting for voice signature...');
  const [isListeningSim, setIsListeningSim] = useState(false);
  const [soundwaves, setSoundwaves] = useState<number[]>([12, 10, 8, 14, 22, 11, 7, 12, 18, 15, 9]);
  const [micSensitivity, setMicSensitivity] = useState<number>(-35); // decibel threshold from -60 to -10, defaults to -35

  // Custom Wake Word and Background Listening states
  const [wakeWord, setWakeWord] = useState<string>('scribe');
  const [isWakeWordActive, setIsWakeWordActive] = useState<boolean>(true);
  const [isRecordingWakeWord, setIsRecordingWakeWord] = useState<boolean>(false);
  const [recordingCountdown, setRecordingCountdown] = useState<number>(0);
  const [wakeWordInput, setWakeWordInput] = useState<string>('scribe');
  const [simulatedWakeText, setSimulatedWakeText] = useState<string>('');

  // Revert/Undo Action State Managers
  const [lastCommandId, setLastCommandId] = useState<string | null>(null);
  const [lastCommandLabel, setLastCommandLabel] = useState<string | null>(null);
  const [undoBackupTranscripts, setUndoBackupTranscripts] = useState<CachedTranslation[]>([]);
  const [previousTabId, setPreviousTabId] = useState<'dialogue' | 'call_translator' | 'mascot' | null>(null);

  // Recent Voice Commands state
  interface ExecutedVoiceCommand {
    id: string;
    phrase: string;
    matchedText: string;
    status: 'Executed' | 'Reverted' | 'Unrecognized' | 'Error';
    timestamp: string;
    commandId?: string;
  }
  const [recentCommands, setRecentCommands] = useState<ExecutedVoiceCommand[]>([]);
  const [lastAutoSaved, setLastAutoSaved] = useState<string | null>(null);

  // Battery-Saving Mode states: monitoring battery level, providing simulated override and toggle
  const [hardwareBatteryLevel, setHardwareBatteryLevel] = useState<number>(1.0); // 1.0 = 100%
  const [simulatedBatteryLevel, setSimulatedBatteryLevel] = useState<number | null>(null); // null means use real hardware
  const [isBatterySavingActive, setIsBatterySavingActive] = useState<boolean>(true); // user can toggle auto saver

  const effectiveBatteryLevel = simulatedBatteryLevel !== null ? simulatedBatteryLevel : hardwareBatteryLevel;
  const isBatteryLow = effectiveBatteryLevel < 0.20; // < 20%
  const batterySavingMode = isBatteryLow && isBatterySavingActive;

  // Traveler 36-Hour Crash Language Course states
  const [isCourseExpanded, setIsCourseExpanded] = useState<boolean>(false);
  const [selectedCourseId, setSelectedCourseId] = useState<string>('hi');
  const [selectedCategory, setSelectedCategory] = useState<string>('All');

  // User Verification and Login Gateway States
  const [isLoggedIn, setIsLoggedIn] = useState<boolean>(false);
  const [loginEmail, setLoginEmail] = useState<string>('');
  const [loginMobile, setLoginMobile] = useState<string>('');
  const [loginMethod, setLoginMethod] = useState<'email' | 'mobile'>('email');
  const [termsAccepted, setTermsAccepted] = useState<boolean>(false);

  // Crowdsourced Verification Feedback and Rewards State Managers
  const [walletCredits, setWalletCredits] = useState<number>(100); // 100 Initial credits
  const [pendingPoints, setPendingPoints] = useState<number>(0);
  const [isAuditorHubExpanded, setIsAuditorHubExpanded] = useState<boolean>(false);
  const [fbLanguage, setFbLanguage] = useState<string>('hi');
  const [fbOriginal, setFbOriginal] = useState<string>('');
  const [fbTranslated, setFbTranslated] = useState<string>('');
  const [fbIsAccurate, setFbIsAccurate] = useState<boolean>(true);
  const [fbCorrection, setFbCorrection] = useState<string>('');

  interface TranslationFeedbackItem {
    id: string;
    language: string;
    originalText: string;
    translatedText: string;
    accuracyState: 'accurate' | 'inaccurate';
    userCorrection: string;
    verificationStatus: 'Pending Verification' | 'Verified & Credits Issued';
    pointsReward: number;
    timestamp: string;
  }
  const [feedbackHistory, setFeedbackHistory] = useState<TranslationFeedbackItem[]>([
    {
      id: 'fb_init_1',
      language: 'Hindi',
      originalText: 'Please help me immediately.',
      translatedText: 'कृपया मेरी तुरंत मदद करें।',
      accuracyState: 'accurate',
      userCorrection: '',
      verificationStatus: 'Verified & Credits Issued',
      pointsReward: 50,
      timestamp: 'Today at 05:22 AM'
    }
  ]);

  // Weekly earning trend states mimicking Recharts' data structures
  const [weeklyEarnings, setWeeklyEarnings] = useState<{ day: string; credits: number }[]>([
    { day: 'Mon', credits: 50 },
    { day: 'Tue', credits: 100 },
    { day: 'Wed', credits: 50 },
    { day: 'Thu', credits: 150 },
    { day: 'Fri', credits: 100 },
    { day: 'Sat', credits: 120 },
    { day: 'Sun', credits: 100 },
  ]);
  const [selectedChartDay, setSelectedChartDay] = useState<string | null>(null);

  // Sync today's credits on walletCredits updates
  useEffect(() => {
    const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
    const todayName = days[new Date().getDay()];
    setWeeklyEarnings(prev => prev.map(item => {
      if (item.day === todayName) {
        return { ...item, credits: walletCredits };
      }
      return item;
    }));
  }, [walletCredits]);

  const handleSubmitLinguisticReview = async () => {
    if (!fbOriginal.trim() || !fbTranslated.trim()) {
      Alert.alert('Incomplete Form', 'Please enter both the original English sentence and the translated native text.');
      return;
    }

    const matchedLang = LANGUAGE_COURSES.find(c => c.id === fbLanguage) || LANGUAGE_COURSES[0];
    const newFeedback: TranslationFeedbackItem = {
      id: Math.random().toString(36).substring(2, 9),
      language: matchedLang.name,
      originalText: fbOriginal.trim(),
      translatedText: fbTranslated.trim(),
      accuracyState: fbIsAccurate ? 'accurate' : 'inaccurate',
      userCorrection: fbIsAccurate ? '' : fbCorrection.trim(),
      verificationStatus: 'Pending Verification',
      pointsReward: 50,
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
    };

    setFeedbackHistory(prev => [newFeedback, ...prev]);
    setPendingPoints(prev => prev + 50);

    // Clear Form Setup
    setFbOriginal('');
    setFbTranslated('');
    setFbIsAccurate(true);
    setFbCorrection('');

    try {
      await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    } catch (e) {}

    speakText("Verification review logged. Fifty pending points added to wallet waiting for team validation.");
    Alert.alert(
      'Review Registered 📝',
      'Linguistic verification review successfully submitted! You have earned +50 Pending Points. Once verified by the AnuVedhai audit team, these will automatically be converted to Active Reward Credits!'
    );
  };

  const handleSimulateTeamLinguisticAudit = async () => {
    const pendingItemsCount = feedbackHistory.filter(item => item.verificationStatus === 'Pending Verification').length;
    if (pendingItemsCount === 0) {
      Alert.alert('No Pending Reviews', 'All translation feedbacks have been verified or no reviews are pending.');
      return;
    }

    setFeedbackHistory(prev => prev.map(item => {
      if (item.verificationStatus === 'Pending Verification') {
        return { ...item, verificationStatus: 'Verified & Credits Issued' };
      }
      return item;
    }));

    const newlyCreditedAmount = pendingPoints;
    setWalletCredits(prev => prev + newlyCreditedAmount);
    setPendingPoints(0);

    try {
      await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    } catch (e) {}

    speakText(`Evaluation verification complete. ${newlyCreditedAmount} active reward credits successfully transferred to your wallet.`);
    Alert.alert(
      'Curation Verification Success 💎',
      `The team verified your ${pendingItemsCount} crowdsourced translation review(s)! ${newlyCreditedAmount} Credits have been deposited into your active account wallet.`
    );
  };

  // Interactive Vocabulary Practice Quiz states
  const [activeCourseSubTab, setActiveCourseSubTab] = useState<'learn' | 'quiz'>('learn');
  const [quizQuestionPhrase, setQuizQuestionPhrase] = useState<CoursePhrase | null>(null);
  const [quizOptions, setQuizOptions] = useState<string[]>([]);
  const [quizSelectedOption, setQuizSelectedOption] = useState<string | null>(null);
  const [quizIsCorrect, setQuizIsCorrect] = useState<boolean | null>(null);
  const [quizScore, setQuizScore] = useState<{ correct: number; total: number }>({ correct: 0, total: 0 });

  const triggerNextQuizQuestion = (courseId = selectedCourseId, category = selectedCategory) => {
    const currentCourse = LANGUAGE_COURSES.find(c => c.id === courseId) || LANGUAGE_COURSES[0];
    let pool = currentCourse.phrases;
    if (category !== 'All') {
      const splitCat = category.split(' ')[0];
      pool = currentCourse.phrases.filter(p => p.category.includes(splitCat));
    }
    
    if (pool.length === 0) {
      pool = currentCourse.phrases;
    }

    const correctPhrase = pool[Math.floor(Math.random() * pool.length)];
    if (!correctPhrase) return;

    // Distractors from other phrases in the same course
    const otherPhrases = currentCourse.phrases.filter(p => p.id !== correctPhrase.id);
    const shuffledOthers = [...otherPhrases].sort(() => 0.5 - Math.random());
    const distractors = shuffledOthers.slice(0, Math.min(3, shuffledOthers.length));
    
    // Combine and shuffle native translation options
    const options = [correctPhrase, ...distractors]
      .map(p => p.native)
      .sort(() => 0.5 - Math.random());

    setQuizQuestionPhrase(correctPhrase);
    setQuizOptions(options);
    setQuizSelectedOption(null);
    setQuizIsCorrect(null);
  };

  useEffect(() => {
    if (activeCourseSubTab === 'quiz') {
      triggerNextQuizQuestion(selectedCourseId, selectedCategory);
    }
  }, [selectedCourseId, selectedCategory, activeCourseSubTab]);

  // Simulate looping microphone audio level changes
  useEffect(() => {
    let interval: NodeJS.Timeout | null = null;
    if (isListeningSim || isRecordingWakeWord) {
      const stepMs = batterySavingMode ? 400 : 100; // Slow down animation from 100ms to 400ms to save CPU
      interval = setInterval(() => {
        setSoundwaves(Array.from({ length: 11 }, () => Math.floor(Math.random() * 32) + 6));
      }, stepMs);
    } else {
      setSoundwaves([12, 10, 8, 14, 22, 11, 7, 12, 18, 15, 9]);
    }
    return () => {
      if (interval) clearInterval(interval);
    };
  }, [isListeningSim, isRecordingWakeWord, batterySavingMode]);

  // Recording Wake Word Calibration simulation loop
  useEffect(() => {
    let recordingInterval: NodeJS.Timeout | null = null;
    if (isRecordingWakeWord) {
      setRecordingCountdown(3);
      // TTS Speech Prompt to direct the user clearly
      speakText("Acoustic calibration sequence active. Please say your new custom wake word clearly in three, two, one.");
      
      recordingInterval = setInterval(() => {
        setRecordingCountdown(prev => {
          if (prev <= 1) {
            if (recordingInterval) clearInterval(recordingInterval);
            setIsRecordingWakeWord(false);
            const finalWord = wakeWordInput.trim() ? wakeWordInput.trim().toLowerCase() : "scribe";
            setWakeWord(finalWord);
            setNlpFeedbackMsg(`Calibration verified! New custom wake word registered: "${finalWord}"`);
            speakText(`Calibration verified. Custom wake word configured to ${finalWord}`);
            Alert.alert(
              'Calibration Complete', 
              `Custom voice signature matching trigger updated to "${finalWord}" successfully!`
            );
            return 0;
          }
          return prev - 1;
        });
      }, 1500);
    }
    return () => {
      if (recordingInterval) clearInterval(recordingInterval);
    };
  }, [isRecordingWakeWord]);

  const handleSimulateWakeWord = async (inputWord: string) => {
    if (!inputWord.trim()) return;
    const cleaned = inputWord.toLowerCase().trim();
    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    
    if (cleaned === wakeWord.toLowerCase()) {
      await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy);
      setVoiceCommandMode(true);
      setIsListeningSim(true);
      setNlpFeedbackMsg(`Hands-free wake word "${wakeWord}" detected! Vocal streams active.`);
      speakText(`${wakeWord} detected. Speak your command now.`);
      setSimulatedWakeText('');
    } else {
      setNlpFeedbackMsg(`Mismatched trigger signature: "${inputWord}". Expected: "${wakeWord}".`);
      speakText(`Mismatched noise signature. Please try saying ${wakeWord}.`);
    }
  };

  // Check network connectivity on load and subscribe
  useEffect(() => {
    async function checkNetwork() {
      const state = await Network.getNetworkStateAsync();
      setIsConnected(state.isConnected ?? false);
    }
    
    checkNetwork();
    
    // Low-overhead network status polling since we are wrapping an online-first web API translator
    const interval = setInterval(async () => {
      const state = await Network.getNetworkStateAsync();
      if (state.isConnected !== isConnected) {
        setIsConnected(state.isConnected ?? false);
      }
    }, 6000);

    return () => clearInterval(interval);
  }, [isConnected]);

  // Load real hardware battery level and register live level listener
  useEffect(() => {
    let sub: any = null;
    async function startBatteryMonitoring() {
      try {
        const isAvailable = await Battery.isAvailableAsync();
        if (isAvailable) {
          const initialLevel = await Battery.getBatteryLevelAsync();
          setHardwareBatteryLevel(initialLevel);

          sub = Battery.addBatteryLevelListener(({ batteryLevel }) => {
            setHardwareBatteryLevel(batteryLevel);
          });
        }
      } catch (err) {
        console.warn('[Battery] Hardware battery sensor initialization failed:', err);
      }
    }
    startBatteryMonitoring();
    return () => {
      if (sub) {
        sub.remove();
      }
    };
  }, []);

  // Load offline data when connection terminates
  useEffect(() => {
    if (isConnected === false) {
      getTranscriptsCache().then(data => {
        setCachedTranscripts(data);
      });
    }
  }, [isConnected]);

  // Recovery of any crashed session transcripts on initial startup
  useEffect(() => {
    async function recoverState() {
      try {
        const { recoverCrashedSessionState } = require('./src/utils/SecureSave');
        const recoveryResult = await recoverCrashedSessionState();
        if (recoveryResult && recoveryResult.recoveredCount > 0) {
          console.log(`[Startup] Recovered ${recoveryResult.recoveredCount} transcript entries from SecureStore!`);
          
          // Refresh localized transcripts cache state
          const refreshed = await getTranscriptsCache();
          setCachedTranscripts(refreshed);
          
          Alert.alert(
            'Session Recovered 🛡️',
            `An unexpected app exit was detected. We have successfully restored ${recoveryResult.recoveredCount} unsaved translation transcripts to your local history cache!`
          );
        }
      } catch (err) {
        console.error('[Startup] Crash recovery routine error on startup:', err);
      }
    }
    recoverState();
  }, []);

  // Periodic auto-save of current transcripts to SecureStore (Battery aware)
  useEffect(() => {
    const ms = batterySavingMode ? 120000 : 30000; // 120 seconds vs 30 seconds
    const autoSaveInterval = setInterval(async () => {
      try {
        const currentData = await getTranscriptsCache();
        if (currentData && currentData.length > 0) {
          const { autoSaveToSecureStore } = require('./src/utils/SecureSave');
          const success = await autoSaveToSecureStore(currentData);
          if (success) {
            const now = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
            setLastAutoSaved(now);
          }
        }
      } catch (err) {
        console.error('[Interval] Periodical auto-save caught an error:', err);
      }
    }, ms);

    return () => clearInterval(autoSaveInterval);
  }, [batterySavingMode]);

  // Handle hardware Back Button on Android devices (Double tap back to exit if at root, else navigate web history)
  useEffect(() => {
    const onBackPress = () => {
      if (canGoBack && webViewRef.current) {
        webViewRef.current.goBack();
        return true; // prevent default behavior
      }
      return false; // let system go back / exit
    };

    if (Platform.OS === 'android') {
      BackHandler.addEventListener('hardwareBackPress', onBackPress);
    }

    return () => {
      if (Platform.OS === 'android') {
        BackHandler.removeEventListener('hardwareBackPress', onBackPress);
      }
    };
  }, [canGoBack]);

  // Handle navigation updates from WebView
  const handleNavigationStateChange = (navState: WebViewNavigation) => {
    setCanGoBack(navState.canGoBack);
    setCurrentUrl(navState.url);
  };

  // Triggers reload of the WebView wrapper container
  const handleRefresh = () => {
    Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    webViewRef.current?.reload();
  };

  // Copy current URL to clipboard
  const handleCopyLink = async () => {
    await Clipboard.setStringAsync(currentUrl);
    Alert.alert('Link Copied', 'The active translation workspace link was successfully copied to your system clipboard!', [
      { text: 'OK' }
    ]);
  };

  const handleClearLocalCache = async () => {
    Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy);
    const success = await clearTranscriptsCache();
    if (success) {
      try {
        const { clearSecureAutoSave } = require('./src/utils/SecureSave');
        await clearSecureAutoSave();
      } catch (err) {
        console.error('Failed to clear secure auto-save:', err);
      }
      setCachedTranscripts([]);
      setLastAutoSaved(null);
      Alert.alert('History Cleared', 'All local offline transcript fragments have been wiped from your device storage.');
    }
  };

  const handleExecuteVoiceCommand = async (rawPhrase: string) => {
    if (!rawPhrase.trim()) return;
    
    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    setIsListeningSim(false);
    setNlpFeedbackMsg(`Parsing sentence: "${rawPhrase}"...`);
    
    // Check what the current tab is before any navigational change
    let currentVoiceTab: 'dialogue' | 'call_translator' | 'mascot' | null = null;
    if (lastCommandId === 'switch_dialogue') currentVoiceTab = 'dialogue';
    else if (lastCommandId === 'switch_call') currentVoiceTab = 'call_translator';
    else if (lastCommandId === 'switch_mascot') currentVoiceTab = 'mascot';

    try {
      // Create a backup of cache transcripts right before executing the parse voice command
      const transcriptsBackup = [...cachedTranscripts];

      const response = await parseVoiceCommand(
        rawPhrase,
        webViewRef,
        handleRefresh,
        async () => {
          // Clear Action Callback
          await clearTranscriptsCache();
          try {
            const { clearSecureAutoSave } = require('./src/utils/SecureSave');
            await clearSecureAutoSave();
          } catch (err) {
            console.error('Failed to clear secure auto-save:', err);
          }
          setCachedTranscripts([]);
          setLastAutoSaved(null);
          Alert.alert('Hands-Free Clear', 'All cache records deleted off disk successfully!');
        }
      );

      // Track the command in order to support cancelation/rollback
      if (response.commandId === 'clear_offline') {
        setUndoBackupTranscripts(transcriptsBackup);
        setLastCommandId('clear_offline');
        setLastCommandLabel('Cleared Offline Cache');
      } else if (response.commandId.startsWith('switch_')) {
        // Track previous tab to allow reversing tab navigational switches
        if (currentVoiceTab) {
          setPreviousTabId(currentVoiceTab);
        } else {
          // Default fallback assumption for reverse navigation
          setPreviousTabId('dialogue'); 
        }
        setLastCommandId(response.commandId);
        setLastCommandLabel(`Switched to ${response.matchedText}`);
      } else if (response.commandId === 'reload_web' || response.commandId === 'copy_workspace') {
        setLastCommandId(response.commandId);
        setLastCommandLabel(response.matchedText);
      } else {
        // Other command types do not state transitions to undo easily, or speak commands cannot be undone.
      }

      // Append to session's voice command audit list
      const timeString = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
      const statusValue = response.type === 'UNKNOWN' ? 'Unrecognized' : 'Executed';
      const executionRecord: ExecutedVoiceCommand = {
        id: Math.random().toString(36).substring(2, 9),
        phrase: rawPhrase,
        matchedText: response.matchedText,
        status: statusValue as 'Executed' | 'Unrecognized',
        timestamp: timeString,
        commandId: response.commandId
      };
      setRecentCommands(prev => [executionRecord, ...prev].slice(0, 10));

      setNlpFeedbackMsg(`Command: ${response.matchedText}\nStatus: ${response.feedbackSpeech}`);
    } catch (err) {
      setNlpFeedbackMsg(`NLP Parser Interruped: ${err}`);
      
      const timeString = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
      const errorRecord: ExecutedVoiceCommand = {
        id: Math.random().toString(36).substring(2, 9),
        phrase: rawPhrase,
        matchedText: `Error: ${err}`,
        status: 'Error',
        timestamp: timeString
      };
      setRecentCommands(prev => [errorRecord, ...prev].slice(0, 10));
    }
  };

  const handleUndoLastAction = async () => {
    if (!lastCommandId) {
      Alert.alert('No Command', 'There is no active command state registered to be rolled back.');
      return;
    }
    
    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
    const targetUndoId = lastCommandId; // Capture value before clearing states
    
    if (lastCommandId === 'clear_offline') {
      if (undoBackupTranscripts.length > 0) {
        // Save back into local storage cache
        const { saveTranscriptsCache } = require('./src/utils/OfflineCache');
        await saveTranscriptsCache(undoBackupTranscripts);
        setCachedTranscripts(undoBackupTranscripts);
        setUndoBackupTranscripts([]);
        setLastCommandId(null);
        setLastCommandLabel(null);
        speakText("Command canceled. Your previous offline translation history has been restored.");
        Alert.alert('Command Canceled', 'Your previous offline translation history was successfully restored!');
        
        // Update corresponding history entry to 'Reverted'
        setRecentCommands((prevList) => {
          let updated = false;
          return prevList.map((cmd) => {
            if (!updated && cmd.commandId === targetUndoId && cmd.status === 'Executed') {
              updated = true;
              return { ...cmd, status: 'Reverted' };
            }
            return cmd;
          });
        });
      } else {
        Alert.alert('Nothing to Restore', 'No cache records were found in the backup pool.');
      }
    } else if (lastCommandId.startsWith('switch_')) {
      if (previousTabId) {
        webViewRef.current?.postMessage(JSON.stringify({ type: 'SWITCH_TAB', payload: { mode: previousTabId } }));
        webViewRef.current?.injectJavaScript(`
          try {
            const buttons = Array.from(document.querySelectorAll('button'));
            const targetBtn = buttons.find(b => b.textContent && b.textContent.toLowerCase().includes('${previousTabId === 'dialogue' ? 'dialogue' : previousTabId === 'mascot' ? 'mascot' : 'call'}'));
            if (targetBtn) { targetBtn.click(); }
          } catch (err) {}
          true;
        `);
        const prev = previousTabId;
        setPreviousTabId(null);
        setLastCommandId(null);
        setLastCommandLabel(null);
        speakText("Command canceled. Returned back to previous screen navigation tab.");
        Alert.alert('Command Canceled', `Reverted tab view back to ${prev === 'dialogue' ? 'Dialogue Mode' : prev === 'mascot' ? 'Mascot Buddy' : 'Call Suite'}.`);
        
        // Update corresponding history entry to 'Reverted'
        setRecentCommands((prevList) => {
          let updated = false;
          return prevList.map((cmd) => {
            if (!updated && cmd.commandId === targetUndoId && cmd.status === 'Executed') {
              updated = true;
              return { ...cmd, status: 'Reverted' };
            }
            return cmd;
          });
        });
      } else {
        Alert.alert('Cannot Undo', 'Previous tab location was not stored.');
      }
    } else {
      Alert.alert('Cannot Undo', `Command "${lastCommandLabel || lastCommandId}" handles immutable activities that cannot be hot-swapped.`);
    }
  };

  // Fallback screen when client is offline (enriched with active Local storage backup reader)
  if (!isConnected) {
    return (
      <SafeAreaView style={styles.errorContainer}>
        <StatusBar barStyle="light-content" backgroundColor="#121212" />
        
        <View style={styles.offlineHeader}>
          <Text style={styles.emojiIcon}>📡</Text>
          <Text style={styles.errorTitle}>Connection Terminated</Text>
          <Text style={styles.errorSubtitle}>
            AnuVedhai requires active internet connection to communicate with LLM translation bridges.
          </Text>
          
          <View style={styles.buttonRow}>
            <TouchableOpacity
              style={styles.retryButton}
              onPress={async () => {
                const state = await Network.getNetworkStateAsync();
                setIsConnected(state.isConnected ?? false);
                if (state.isConnected) {
                  Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
                }
              }}
            >
              <Text style={styles.retryText}>REFRESH SIGNAL</Text>
            </TouchableOpacity>

            {cachedTranscripts.length > 0 && (
              <TouchableOpacity
                style={styles.clearCacheBtn}
                onPress={handleClearLocalCache}
              >
                <Text style={styles.clearCacheTxt}>CLEAR OFFLINE</Text>
              </TouchableOpacity>
            )}
          </View>
        </View>

        {/* Local database backup list */}
        <View style={styles.historySection}>
          <Text style={styles.historyHeading}>
            📂 Local Storage Cache ({cachedTranscripts.length} Transcripts)
          </Text>
          
          {cachedTranscripts.length === 0 ? (
            <View style={styles.emptyCacheState}>
              <Text style={styles.emptyCacheText}>
                No cached conversations detected. Successful translations performed while online are automatically persisted here for offline browsing.
              </Text>
            </View>
          ) : (
            <FlatList
              data={cachedTranscripts}
              keyExtractor={(item) => item.id}
              contentContainerStyle={{ paddingBottom: 24 }}
              renderItem={({ item }) => (
                <View style={styles.transcriptCard}>
                  <View style={styles.cardHeader}>
                    <Text style={styles.langLabel}>
                      {item.sourceLang} ➔ {item.targetLang}
                    </Text>
                    {item.timestamp ? (
                      <Text style={styles.timeLabel}>
                        {new Date(item.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                      </Text>
                    ) : null}
                  </View>
                  
                  <View style={styles.cardSection}>
                    <Text style={styles.textHeading}>ORIGINAL TEXT</Text>
                    <Text style={styles.textBody}>{item.originalText}</Text>
                  </View>
                  
                  <View style={styles.lineDivider} />
                  
                  <View style={styles.cardSection}>
                    <Text style={styles.transHeading}>TRANSLATION</Text>
                    <Text style={styles.transBody}>{item.translatedText}</Text>
                  </View>
                </View>
              )}
            />
          )}
        </View>
      </SafeAreaView>
    );
  }

  // Login Interception Gate
  if (!isLoggedIn) {
    const handleLoginPress = async () => {
      // Validate inputs based on method choice
      if (loginMethod === 'email') {
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!loginEmail.trim() || !emailRegex.test(loginEmail)) {
          Alert.alert('Invalid Email ID', 'Please enter a valid email address (e.g., username@domain.com) to authenticate.');
          return;
        }
      } else {
        const numbersOnly = loginMobile.replace(/\D/g, '');
        if (numbersOnly.length < 10) {
          Alert.alert('Invalid Mobile Connection', 'Please enter a valid mobile phone number containing at least 10 digits.');
          return;
        }
      }

      if (!termsAccepted) {
        Alert.alert('Agreement Required', 'Please read the translation disclaimer and click the agreement checkbox to authorize secure portal deployment.');
        return;
      }

      try {
        await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      } catch (e) {}

      setIsLoggedIn(true);
      speakText("Authorization verified. Welcoming back to Scribe translation core.");
    };

    return (
      <SafeAreaView style={styles.loginPageContainer}>
        <StatusBar barStyle="light-content" backgroundColor="#0B0F19" />
        
        <ScrollView 
          contentContainerStyle={styles.loginScrollContainer} 
          keyboardShouldPersistTaps="handled"
          indicatorStyle="white"
        >
          <View style={styles.loginLogoSection}>
            <Text style={styles.loginAppIcon}>🌐</Text>
            <Text style={styles.loginAppName}>AnuVedhai Gateway</Text>
            <Text style={styles.loginAppTagline}>Proximity Indian Conversational Translator — Scribe Active Engine</Text>
          </View>

          <View style={styles.loginCard}>
            <Text style={styles.loginCardTitle}>Secure Portal Authorization</Text>
            <Text style={styles.loginCardDesc}>Enter your authentication coordinates to unlock near-realtime linguistic modules.</Text>

            {/* Login Tab Selectors */}
            <View style={styles.loginTabGroup}>
              <TouchableOpacity
                style={[styles.loginTabBtn, loginMethod === 'email' && styles.loginTabBtnActive]}
                onPress={async () => {
                  try { await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); } catch(e){}
                  setLoginMethod('email');
                }}
                activeOpacity={0.7}
              >
                <Text style={[styles.loginTabBtnTxt, loginMethod === 'email' && styles.loginTabBtnTxtActive]}>
                  📧 Email ID
                </Text>
              </TouchableOpacity>

              <TouchableOpacity
                style={[styles.loginTabBtn, loginMethod === 'mobile' && styles.loginTabBtnActive]}
                onPress={async () => {
                  try { await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); } catch(e){}
                  setLoginMethod('mobile');
                }}
                activeOpacity={0.7}
              >
                <Text style={[styles.loginTabBtnTxt, loginMethod === 'mobile' && styles.loginTabBtnTxtActive]}>
                  📱 Mobile ID
                </Text>
              </TouchableOpacity>
            </View>

            {/* Dynamic Inputs */}
            {loginMethod === 'email' ? (
              <View style={styles.loginInputWrapper}>
                <Text style={styles.loginInputLabel}>Authorized Email Address</Text>
                <TextInput
                  style={styles.loginTextInputField}
                  placeholder="e.g. officer@anuvedhai.in"
                  placeholderTextColor="#4E5D78"
                  value={loginEmail}
                  onChangeText={setLoginEmail}
                  autoCapitalize="none"
                  autoCorrect={false}
                  keyboardType="email-address"
                  keyboardAppearance="dark"
                />
              </View>
            ) : (
              <View style={styles.loginInputWrapper}>
                <Text style={styles.loginInputLabel}>Corporate Mobile Number</Text>
                <TextInput
                  style={styles.loginTextInputField}
                  placeholder="e.g. +91 98765 43210"
                  placeholderTextColor="#4E5D78"
                  value={loginMobile}
                  onChangeText={setLoginMobile}
                  keyboardType="phone-pad"
                  keyboardAppearance="dark"
                />
              </View>
            )}

            {/* Terms and Conditions Section */}
            <View style={styles.termsTitledBox}>
              <Text style={styles.termsTitleText}>⚠️ LINGUISTIC ACCURACY DISCLAIMER & REWARD AUDIT POLICY</Text>
              <ScrollView style={styles.termsScrollBox} nestedScrollEnabled={true}>
                <Text style={styles.termsBodyText}>
                  1. <Text style={{fontWeight: 'bold', color: '#00E5FF'}}>Non-Human Certified Outputs:</Text> All conversational transcript translations and audio synthesized files served via AnuVedhai are dynamically formulated using remote Large Language Models (LLM API routers). These translations have NOT been certified or verified as perfect by linguistic authorities beforehand.
                  {"\n\n"}
                  2. <Text style={{fontWeight: 'bold', color: '#00E5FF'}}>Potential Discrepancies Warning:</Text> Users are formally advised that translation results may exhibit minor contextual discrepancies, grammatical variances, or matching differences depending on regional slang, localized accent registers, and complex cultural terminologies.
                  {"\n\n"}
                  3. <Text style={{fontWeight: 'bold', color: '#00E5FF'}}>Linguistic Auditor Program:</Text> To guarantee bulletproof, field-ready local dialects, you are strongly encouraged to report linguistic feedback. When you spot a translation match—be it accurate or inaccurate—please submit a verification report through the "Audit & Rewards Hub" inside the app dashboard.
                  {"\n\n"}
                  4. <Text style={{fontWeight: 'bold', color: '#00E5FF'}}>Earn Points & Wallet Rewards Credits:</Text> Reviewing and reporting results immediately deposits <Text style={{color: '#FF9800', fontWeight: 'bold'}}>50 Pending Points</Text> into your auditor wallet. Once checked, curated, and verified by our auditing team, these pending points are transformed into <Text style={{color: '#00E676', fontWeight: 'bold'}}>Active Reward Credits</Text> which can be used to purchase priority low-latency servers and custom synthetic voice packets!
                </Text>
              </ScrollView>
            </View>

            {/* Terms Consenting Box */}
            <TouchableOpacity
              style={styles.checkboxLine}
              onPress={async () => {
                try { await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); } catch(e){}
                setTermsAccepted(!termsAccepted);
              }}
              activeOpacity={0.8}
            >
              <View style={[styles.checkboxSquare, termsAccepted && styles.checkboxSquareChecked]}>
                {termsAccepted && <Text style={styles.checkboxTick}>✓</Text>}
              </View>
              <Text style={styles.checkboxLabel}>
                Understood. I accept that translations are experimental and consent to the Crowdsourced Accuracy Verification Policy.
              </Text>
            </TouchableOpacity>

            {/* Portal Action Trigger Button */}
            <TouchableOpacity
              style={[styles.loginSubmitBtn, !termsAccepted && styles.loginSubmitBtnDisabled]}
              onPress={handleLoginPress}
              activeOpacity={0.8}
            >
              <Text style={styles.loginSubmitBtnTxt}>AUTHORIZE DEPLOYMENT TERMINAL</Text>
            </TouchableOpacity>
          </View>
        </ScrollView>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" backgroundColor="#1A1A1A" />
      
      {/* Visual Header shell offering quick system controls to users inside iOS/Android */}
      <NativeHeader 
        onRefresh={handleRefresh}
        onCopyLink={handleCopyLink}
        currentUrl={currentUrl}
      />

      <View style={styles.webWrapper}>
        <WebView
          ref={webViewRef}
          source={{ uri: WEB_APP_URL }}
          style={styles.webview}
          onNavigationStateChange={handleNavigationStateChange}
          onLoadStart={() => setIsLoading(true)}
          onLoadEnd={() => setIsLoading(false)}
          domStorageEnabled={true}
          javaScriptEnabled={true}
          allowsInlineMediaPlayback={true}
          mediaPlaybackRequiresUserAction={false}
          startInLoadingState={true}
          renderLoading={() => (
            <View style={styles.nativeLoader}>
              <ActivityIndicator size="large" color="#FF9800" />
              <Spacer height={14} />
              <Text style={styles.loaderTxt}>Initializing AnuVedhai Digital Scribe...</Text>
            </View>
          )}
          // Web to Native communication bridge
          onMessage={(event) => {
            handleNativeBridgeMessage(event.nativeEvent.data);
          }}
        />

        {/* Voice Hands-Free Floating Trigger & Background Wake Word Sensor Monitor */}
        {!voiceCommandMode && (
          <View style={styles.wakeWordSensorDock}>
            {/* Background Radar Pulse Monitor */}
            <View style={styles.radarMonitorRow}>
              <View style={[styles.radarLed, isWakeWordActive ? styles.radarLedOn : styles.radarLedOff]} />
              <Text style={styles.radarMonitorText}>
                {isWakeWordActive 
                  ? `🎙️ Wake Radar ACTIVE (Speak/Type "${wakeWord.toUpperCase()}")` 
                  : "🎙️ Wake Radar: DISABLED (Manual Touch Mode)"
                }
              </Text>
              {isBatteryLow && (
                <Text style={{ marginLeft: 'auto', color: '#FF5252', fontSize: 8, fontWeight: '950', letterSpacing: 0.2 }}>
                  ⚠️ ECO SAVING ACTIVE: {Math.round(effectiveBatteryLevel * 100)}%
                </Text>
              )}
            </View>

            {isWakeWordActive ? (
              <View style={styles.wakeSimRow}>
                <TextInput
                  style={styles.wakeSimInput}
                  value={simulatedWakeText}
                  onChangeText={setSimulatedWakeText}
                  onSubmitEditing={() => handleSimulateWakeWord(simulatedWakeText)}
                  placeholder={`Speak or type "${wakeWord}"...`}
                  placeholderTextColor="#7F7F7F"
                  keyboardAppearance="dark"
                />
                <TouchableOpacity
                  style={styles.wakeSimBtn}
                  onPress={() => handleSimulateWakeWord(simulatedWakeText)}
                  activeOpacity={0.7}
                >
                  <Text style={styles.wakeSimBtnTxt}>Speak 🗣️</Text>
                </TouchableOpacity>
              </View>
            ) : (
              <View style={styles.wakeSimDisabledBox}>
                <Text style={styles.wakeSimDisabledTxt}>
                  Auto-detection offline. Press force toggle below or re-enable wake gate.
                </Text>
              </View>
            )}

            <TouchableOpacity
              style={styles.floatingMicBtnInline}
              onPress={async () => {
                await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Heavy);
                setVoiceCommandMode(true);
                setIsListeningSim(true);
                setNlpFeedbackMsg('Microphone sensor active. Simulated voice feed is streaming.');
                speakText("Voice Command Mode active. Speak now or select a preset below.");
              }}
              activeOpacity={0.8}
            >
              <Text style={styles.floatingMicText}>🎙️ Force Manual Voice Override</Text>
            </TouchableOpacity>
          </View>
        )}

        {/* Dynamic Voice Control Overlay */}
        {voiceCommandMode && (
          <View style={styles.voiceCommandPanel}>
            <View style={styles.voiceHeader}>
              <View style={styles.voiceTitleRow}>
                <View style={[styles.pulseCircle, { opacity: isListeningSim ? 1 : 0.4 }]} />
                <Text style={styles.voiceTitle}>Scribe Hands-Free Core</Text>
              </View>
              <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                {lastCommandId && (
                  <TouchableOpacity
                    onPress={handleUndoLastAction}
                    style={[styles.closeVoiceBtn, { marginRight: 8, backgroundColor: 'rgba(255, 152, 0, 0.15)', borderColor: 'rgba(255, 152, 0, 0.3)', borderWidth: 1 }]}
                  >
                    <Text style={{ color: '#FF9800', fontSize: 10, fontWeight: '700' }}>↩️ UNDO LAST</Text>
                  </TouchableOpacity>
                )}
                <TouchableOpacity
                  onPress={async () => {
                    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
                    setVoiceCommandMode(false);
                    setIsListeningSim(false);
                    await Speech.stop();
                  }}
                  style={styles.closeVoiceBtn}
                >
                  <Text style={styles.closeVoiceTxt}>✕ CLOSE</Text>
                </TouchableOpacity>
              </View>
            </View>

            {/* Simulated Animated Soundwave Display */}
            <View style={styles.soundwaveModule}>
              {soundwaves.map((height, idx) => {
                const thresholdHeightVal = 8 + Math.round(((micSensitivity - (-60)) / 50) * 26);
                const isCrossingThreshold = height >= thresholdHeightVal;
                return (
                  <View
                    key={idx}
                    style={[
                      styles.soundbar,
                      {
                        height: height,
                        opacity: isListeningSim ? 1 : 0.4,
                        backgroundColor: isListeningSim && isCrossingThreshold ? '#FF9800' : 'rgba(255, 255, 255, 0.12)'
                      }
                    ]}
                  />
                );
              })}
              {/* Horizontal decibel line indicator crossing the soundwave representation */}
              <View
                style={[
                  styles.thresholdLine,
                  { bottom: 8 + Math.round(((micSensitivity - (-60)) / 50) * 26) }
                ]}
                pointerEvents="none"
              >
                <View style={styles.thresholdDashedLine} />
                <View style={styles.thresholdPill}>
                  <Text style={styles.thresholdLabel}>Gate Limit: {micSensitivity} dB</Text>
                </View>
              </View>
            </View>

            {/* Microphone Sensitivity Slider Controls */}
            <View style={styles.sensitivityContainer}>
              <View style={styles.sensitivityHeaderRow}>
                <Text style={styles.sensitivityTitle}>🎙️ MIC SENSITIVITY TRIGGER LIMIT</Text>
                <Text style={styles.sensitivityBadge}>
                  {micSensitivity === -60 ? 'MAX SENSIBLE (Whispers)' : micSensitivity === -10 ? 'MAX FILTER (Noisy Outdoor)' : `${micSensitivity} dB Threshold`}
                </Text>
              </View>

              <View style={styles.sliderInteractiveRow}>
                {/* Decrement Button */}
                <TouchableOpacity
                  style={styles.adjustBtn}
                  onPress={async () => {
                    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
                    setMicSensitivity(prev => Math.max(-60, prev - 5));
                  }}
                  activeOpacity={0.7}
                >
                  <Text style={styles.adjustBtnTxt}>⚙️ Low-db</Text>
                </TouchableOpacity>

                {/* Slider Track with Segmented Snappers */}
                <View style={styles.sliderTrackWrapper}>
                  <View style={styles.sliderBaseLine} />
                  {/* Current Active Fill Indicator */}
                  <View 
                    style={[
                      styles.sliderActiveFill, 
                      { width: `${((micSensitivity - (-60)) / 50) * 100}%` }
                    ]} 
                  />
                  {/* 6 Segment Snapping Touch Dots */}
                  {[-60, -50, -40, -30, -20, -10].map((dbVal) => {
                    const isSelected = micSensitivity === dbVal;
                    const dotLeftPos = `${((dbVal - (-60)) / 50) * 100}%`;
                    return (
                      <TouchableOpacity
                        key={dbVal}
                        style={[
                          styles.snapDotTouchArea,
                          { left: dotLeftPos }
                        ]}
                        onPress={async () => {
                          await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
                          setMicSensitivity(dbVal);
                        }}
                        activeOpacity={1}
                      >
                        <View 
                          style={[
                            styles.snapDotCircle,
                            isSelected && styles.snapDotActive
                          ]}
                        />
                        <Text style={[styles.snapDotText, isSelected && styles.snapDotTextActive]}>
                          {dbVal}
                        </Text>
                      </TouchableOpacity>
                    );
                  })}
                </View>

                {/* Increment Button */}
                <TouchableOpacity
                  style={styles.adjustBtn}
                  onPress={async () => {
                    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
                    setMicSensitivity(prev => Math.min(-10, prev + 5));
                  }}
                  activeOpacity={0.7}
                >
                  <Text style={styles.adjustBtnTxt}>Hi-db 🔊</Text>
                </TouchableOpacity>
              </View>
              
              <Text style={styles.sensitivityDesc}>
                {micSensitivity <= -50 
                  ? "🔊 Low Cutoff: Responds to silent whispered audio triggers. Best in tranquil private spaces."
                  : micSensitivity >= -20 
                    ? "🔇 High Cutoff: Ignores wind, typing, or low noise. Recommended inside coffee shops."
                    : "⚙️ Balanced Cutoff: Optimized parameters for typical voice levels in average urban environments."
                }
              </Text>
            </View>

            {/* Set Wake Word Configuration panel */}
            <View style={styles.wakeWordSettingsContainer}>
              <View style={styles.wakeWordSettingsHeader}>
                <Text style={styles.wakeWordSectionTitle}>🗣️ TARGET WAKE WORD CONFIGURATION</Text>
                <TouchableOpacity 
                  style={[styles.radarToggleBtn, isWakeWordActive ? styles.radarToggleBtnActive : styles.radarToggleBtnInactive]}
                  onPress={async () => {
                    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
                    setIsWakeWordActive(!isWakeWordActive);
                    speakText(!isWakeWordActive ? `Background wake word active. Listening for keyword "${wakeWord}".` : "Background wake word detection disabled.");
                  }}
                >
                  <Text style={styles.radarToggleTxt}>
                    {isWakeWordActive ? "📡 Auto-On" : "🔒 Touch-Only"}
                  </Text>
                </TouchableOpacity>
              </View>

              <View style={styles.wakeWordQuickConfigRow}>
                <Text style={styles.wakeWordLabel}>Hands-Free Phrase Matcher:</Text>
                <View style={styles.wakeWordWordBadge}>
                  <Text style={styles.wakeWordWordBadgeTxt}>"{wakeWord.toUpperCase()}"</Text>
                </View>
              </View>

              {/* Word Calibration/Recording Interface */}
              {isRecordingWakeWord ? (
                <View style={styles.calibratingWrapper}>
                  <ActivityIndicator size="small" color="#FF3D00" style={{ marginRight: 6 }} />
                  <Text style={styles.calibratingStateTxt}>
                    🎙️ CALIBRATING AUDIO... SAY "{wakeWordInput.toUpperCase()}" ({recordingCountdown}s)
                  </Text>
                </View>
              ) : (
                <View style={styles.wakeInputActionsWrapper}>
                  <TextInput
                    style={styles.wakeWordActionInput}
                    value={wakeWordInput}
                    onChangeText={setWakeWordInput}
                    placeholder="Type trigger keyword..."
                    placeholderTextColor="#7F7F7F"
                    keyboardAppearance="dark"
                    autoCapitalize="none"
                    autoCorrect={false}
                  />
                  
                  <TouchableOpacity
                    style={styles.calibrateMicBtn}
                    onPress={async () => {
                      if (!wakeWordInput.trim()) {
                        Alert.alert('Activation Error', 'Please type in the keyword trigger box first before recording!');
                        return;
                      }
                      await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
                      setIsRecordingWakeWord(true);
                    }}
                    activeOpacity={0.8}
                  >
                    <Text style={styles.calibrateMicBtnTxt}>🎙️ Record Word</Text>
                  </TouchableOpacity>
                </View>
              )}

              {/* Predefined Core Quick-Tap Options */}
              <View style={{ marginTop: 6 }}>
                <Text style={styles.quickSelectWordLabel}>Quick vocal hotkeys:</Text>
                <View style={styles.quickWordPresetChipsRow}>
                  {['scribe', 'translate', 'hey scribe', 'buddy'].map((word) => {
                    const isSelected = wakeWord === word;
                    return (
                      <TouchableOpacity
                        key={word}
                        style={[styles.quickWordChip, isSelected && styles.quickWordChipActive]}
                        onPress={async () => {
                          await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
                          setWakeWord(word);
                          setWakeWordInput(word);
                          speakText(`Wake trigger set to ${word}`);
                        }}
                      >
                        <Text style={[styles.quickWordChipTxt, isSelected && styles.quickWordChipTxtActive]}>
                          {word}
                        </Text>
                      </TouchableOpacity>
                    );
                  })}
                </View>
              </View>
            </View>

            {/* Battery Saving Mode Configuration panel */}
            <View style={styles.batterySettingsContainer}>
              <View style={styles.batterySettingsHeader}>
                <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                  <Text style={styles.batteryIcon}>
                    {effectiveBatteryLevel < 0.20 ? '🪫' : '🔋'}
                  </Text>
                  <Text style={styles.batterySectionTitle}>BATTERY CONSERVATION & CO-PROCESSOR</Text>
                </View>
                
                <TouchableOpacity 
                  style={[styles.radarToggleBtn, isBatterySavingActive ? styles.batterySaveActiveBtn : styles.radarToggleBtnInactive]}
                  onPress={async () => {
                    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
                    setIsBatterySavingActive(!isBatterySavingActive);
                    speakText(!isBatterySavingActive ? "Eco Saver configured to automatic guard mode." : "Eco Saver automatic guard mode disabled.");
                  }}
                >
                  <Text style={[styles.radarToggleTxt, isBatterySavingActive && { color: '#00E676' }]}>
                    {isBatterySavingActive ? "❇️ Auto Eco-On" : "🔒 Eco-Off"}
                  </Text>
                </TouchableOpacity>
              </View>

              <View style={styles.batteryInfoRow}>
                <View style={styles.batteryMetricsColumn}>
                  <Text style={styles.batteryStatLabel}>Device State Signature:</Text>
                  <Text style={styles.batteryStatValue}>
                    {Math.round(effectiveBatteryLevel * 100)}% Charge {simulatedBatteryLevel !== null && '(Simulated)'}
                  </Text>
                </View>

                <View style={styles.batteryStatusBadgeWrapper}>
                  {batterySavingMode ? (
                    <View style={styles.ecoBadgeOn}>
                      <Text style={styles.ecoBadgeOnTxt}>⚡ ECO SAVE ACTIVE</Text>
                    </View>
                  ) : (
                    <View style={styles.ecoBadgeOff}>
                      <Text style={styles.ecoBadgeOffTxt}>HIGH POWER</Text>
                    </View>
                  )}
                </View>
              </View>

              {/* Slider or Toggles to simulate battery percentage */}
              <View style={styles.simulatorTitleRow}>
                <Text style={styles.simulatorTitle}>Test battery-saving logic (Simulation overrides):</Text>
              </View>

              <View style={styles.batteryPresetRow}>
                {[1.0, 0.50, 0.15, 0.05].map((level) => {
                  const isSelected = simulatedBatteryLevel === level;
                  const label = `${Math.round(level * 100)}%`;
                  return (
                    <TouchableOpacity
                      key={level}
                      style={[
                        styles.batteryChip, 
                        isSelected && styles.batteryChipActive, 
                        level < 0.20 && isSelected && styles.batteryChipActiveLow
                      ]}
                      onPress={async () => {
                        await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
                        setSimulatedBatteryLevel(level);
                        if (level < 0.20 && isBatterySavingActive) {
                          speakText(`Battery dropped to ${label}. Eco-saving mode active. Lowering animation rate and auto-saving interval.`);
                        }
                      }}
                    >
                      <Text style={[
                        styles.batteryChipTxt, 
                        isSelected && styles.batteryChipTxtActive,
                        level < 0.20 && isSelected && { color: '#FF5252' }
                      ]}>
                        {label} {level < 0.20 ? '⚠️' : '✅'}
                      </Text>
                    </TouchableOpacity>
                  );
                })}

                <TouchableOpacity
                  style={[styles.batteryChip, simulatedBatteryLevel === null && styles.batteryChipActive]}
                  onPress={async () => {
                    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
                    setSimulatedBatteryLevel(null);
                    speakText("Restored live hardware battery monitoring.");
                  }}
                >
                  <Text style={[styles.batteryChipTxt, simulatedBatteryLevel === null && styles.batteryChipTxtActive]}>
                    🛰️ Live
                  </Text>
                </TouchableOpacity>
              </View>

              {/* Display frequency reductions under batterySavingMode */}
              <View style={styles.frequencyFooterRow}>
                <View style={styles.frequencyBox}>
                  <Text style={styles.freqLabel}>Soundwave FPS:</Text>
                  <Text style={[styles.freqValue, batterySavingMode && { color: '#00E676' }]}>
                    {batterySavingMode ? '2.5 Hz (Eco - 400ms)' : '10 Hz (Normal - 100ms)'}
                  </Text>
                </View>
                <View style={styles.frequencyBox}>
                  <Text style={styles.freqLabel}>Auto-Save Sync:</Text>
                  <Text style={[styles.freqValue, batterySavingMode && { color: '#00E676' }]}>
                    {batterySavingMode ? 'Eco: Every 2 mins' : 'Normal: Every 30s'}
                  </Text>
              </View>
            </View>

            {/* Traveler crash course learning section */}
            <View style={styles.courseOuterContainer}>
              <TouchableOpacity
                style={styles.courseHeaderToggle}
                onPress={async () => {
                  try {
                    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
                    setIsCourseExpanded(!isCourseExpanded);
                    if (!isCourseExpanded) {
                      speakText("Welcome to the 36-Hour Traveler Language Accelerator. Select a locale below to master core social phrasings.");
                    }
                  } catch (err) {
                    setIsCourseExpanded(!isCourseExpanded);
                  }
                }}
                activeOpacity={0.8}
              >
                <View style={{ flexDirection: 'row', alignItems: 'center', flex: 1 }}>
                  <Text style={{ fontSize: 13, marginRight: 6 }}>✈️</Text>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.courseTitleMain}>36-HR TRAVELLER LANGUAGE ACCELERATOR</Text>
                    <Text style={styles.courseSubtitleMain}>Pre-trip high-frequency social trainer</Text>
                  </View>
                </View>
                <View style={[styles.expandBadge, isCourseExpanded && styles.expandBadgeActive]}>
                  <Text style={styles.expandBadgeTxt}>{isCourseExpanded ? '✕ CLOSE' : '📖 EXPAND'}</Text>
                </View>
              </TouchableOpacity>

              {isCourseExpanded && (() => {
                const currentCourse = LANGUAGE_COURSES.find(c => c.id === selectedCourseId) || LANGUAGE_COURSES[0];
                const activePhrases = selectedCategory === 'All'
                  ? currentCourse.phrases
                  : currentCourse.phrases.filter(p => p.category.includes(selectedCategory.split(' ')[0]));

                return (
                  <View style={styles.expandedCourseWrapper}>
                    {/* Course Selection Tabs */}
                    <View style={styles.courseTabContainer}>
                      {LANGUAGE_COURSES.map((course) => {
                        const isSelected = selectedCourseId === course.id;
                        return (
                          <TouchableOpacity
                            key={course.id}
                            style={[styles.courseTabButton, isSelected && styles.courseTabButtonActive]}
                            onPress={async () => {
                              try {
                                await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
                              } catch (e) {}
                              setSelectedCourseId(course.id);
                              setSelectedCategory('All');
                              speakText(`Loaded 36-hour crash course for ${course.name}.`);
                            }}
                            activeOpacity={0.7}
                          >
                            <Text style={[styles.courseTabTxt, isSelected && styles.courseTabTxtActive]}>
                              {course.id === 'hi' ? '🇮🇳 ' : course.id === 'es' ? '🇪🇸 ' : course.id === 'fr' ? '🇫🇷 ' : '🇯🇵 '}
                              {course.name}
                            </Text>
                          </TouchableOpacity>
                        );
                      })}
                    </View>

                    {/* Meta Section for Selected Language */}
                    <View style={styles.courseLanguageMetaRow}>
                      <View style={{ flex: 1 }}>
                        <View style={{ flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', marginBottom: 4 }}>
                          <Text style={styles.nativeLanguageTitle}>{currentCourse.nativeName}</Text>
                          <Text style={styles.englishLanguageTitle}> ({currentCourse.name})</Text>
                          
                          {/* Speak native language name button */}
                          <TouchableOpacity
                            style={styles.languageSpeakerBtn}
                            onPress={async () => {
                              try {
                                await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
                              } catch (e) {}
                              speakText(currentCourse.nativeName, { language: currentCourse.locale });
                            }}
                            activeOpacity={0.7}
                          >
                            <Text style={styles.languageSpeakerEmoji}>🔊 Play Name</Text>
                          </TouchableOpacity>
                        </View>
                        <Text style={styles.languageQuickFact}>{currentCourse.quickFact}</Text>
                      </View>
                    </View>

                    {/* 36-Hr Schedule Timeline Visualizer */}
                    <View style={styles.scheduleTimelineBox}>
                      <Text style={styles.scheduleLabel}>⏳ MASTER BLUEPRINT SYNC:</Text>
                      <Text style={styles.scheduleValue}>{currentCourse.hoursSchedule}</Text>
                    </View>

                    {/* Mode Toggle Layout - Learn vs Quiz */}
                    <View style={styles.subTabGroup}>
                      <TouchableOpacity
                        style={[styles.subTabButton, activeCourseSubTab === 'learn' && styles.subTabButtonActive]}
                        onPress={async () => {
                          try { await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); } catch(e){}
                          setActiveCourseSubTab('learn');
                        }}
                        activeOpacity={0.7}
                      >
                        <Text style={[styles.subTabTxt, activeCourseSubTab === 'learn' && styles.subTabTxtActive]}>
                          📚 Study Vocab ({activePhrases.length})
                        </Text>
                      </TouchableOpacity>

                      <TouchableOpacity
                        style={[styles.subTabButton, activeCourseSubTab === 'quiz' && styles.subTabButtonActive]}
                        onPress={async () => {
                          try { await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium); } catch(e){}
                          setActiveCourseSubTab('quiz');
                        }}
                        activeOpacity={0.7}
                      >
                        <Text style={[styles.subTabTxt, activeCourseSubTab === 'quiz' && styles.subTabTxtActive]}>
                          🎯 Practice Quiz
                        </Text>
                      </TouchableOpacity>
                    </View>

                    {activeCourseSubTab === 'learn' ? (
                      <>
                        {/* Phrase Category Selector Filter Tabs */}
                        <View style={styles.categoryFilterRow}>
                          {['All', 'Essentials', 'Transit & Hotels', 'Dining & Social', 'Emergency'].map((cat) => {
                            const isSelected = selectedCategory === cat;
                            return (
                              <TouchableOpacity
                                key={cat}
                                style={[styles.categoryFilterChip, isSelected && styles.categoryFilterChipActive]}
                                onPress={async () => {
                                  try {
                                    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light);
                                  } catch (e) {}
                                  setSelectedCategory(cat);
                                }}
                                activeOpacity={0.7}
                              >
                                <Text style={[styles.categoryFilterChipTxt, isSelected && styles.categoryFilterChipTxtActive]}>
                                  {cat === 'All' ? '🌌 ' : cat === 'Essentials' ? '💡 ' : cat === 'Transit & Hotels' ? '🏨 ' : cat === 'Dining & Social' ? '☕ ' : '🚨 '}
                                  {cat.split(' ')[0]}
                                </Text>
                              </TouchableOpacity>
                            );
                          })}
                        </View>

                        {/* List of high intensity phrases */}
                        <View style={styles.phrasesListingBox}>
                          {activePhrases.length === 0 ? (
                            <Text style={{ color: '#888', fontSize: 10, textAlign: 'center', padding: 8 }}>No vocabulary words in this filter category.</Text>
                          ) : (
                            activePhrases.map((phrase) => {
                              return (
                                <View key={phrase.id} style={styles.phraseItemCard}>
                                  <View style={styles.phraseItemMainColumn}>
                                    <View style={styles.phraseCategoryTagWrapper}>
                                      <Text style={styles.phraseCategoryTagTxt}>{phrase.category.toUpperCase()}</Text>
                                    </View>
                                    <Text style={styles.phraseEnglish}>{phrase.english}</Text>
                                    <Text style={styles.phraseNative}>{phrase.native}</Text>
                                    <Text style={styles.phraseRoman}>Pronunciation: "{phrase.roman}"</Text>
                                  </View>

                                  <TouchableOpacity
                                    style={styles.phraseSpeakerButton}
                                    onPress={async () => {
                                      try {
                                        await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
                                      } catch (e) {}
                                      speakText(phrase.native, { language: currentCourse.locale, rate: 0.82 });
                                    }}
                                    activeOpacity={0.7}
                                  >
                                    <Text style={styles.phraseSpeakerIcon}>🔊 Play accent</Text>
                                  </TouchableOpacity>
                                </View>
                              );
                            })
                          )}
                        </View>
                      </>
                    ) : (
                      /* Practice Quiz Section UI */
                      <View style={styles.quizBoxOuter}>
                        {/* Score and Reset header */}
                        <View style={styles.quizHeaderRow}>
                          <Text style={styles.quizSectionHeader}>📝 ACTIVE ACCELERATOR CHECK</Text>
                          <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                            <Text style={styles.quizScoreBadge}>
                              Score: {quizScore.correct}/{quizScore.total}
                            </Text>
                            <TouchableOpacity
                              style={styles.quizResetLink}
                              onPress={async () => {
                                try { await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium); } catch(e){}
                                setQuizScore({ correct: 0, total: 0 });
                                triggerNextQuizQuestion(selectedCourseId, selectedCategory);
                                speakText("Scoreboards reset.");
                              }}
                            >
                              <Text style={styles.quizResetLinkText}>Reset ⟳</Text>
                            </TouchableOpacity>
                          </View>
                        </View>

                        {quizQuestionPhrase ? (
                          <View style={styles.quizCardInner}>
                            {/* Question Card */}
                            <View style={styles.quizQuestionCard}>
                              <View style={styles.phraseCategoryTagWrapper}>
                                <Text style={styles.phraseCategoryTagTxt}>
                                  {quizQuestionPhrase.category.toUpperCase()}
                                </Text>
                              </View>
                              <Text style={styles.quizQuestionLabel}>How do you translate this phrase to the native script?</Text>
                              <Text style={styles.quizQuestionText}>"{quizQuestionPhrase.english}"</Text>
                            </View>

                            {/* Option Choices */}
                            <View style={styles.quizOptionsGroup}>
                              {quizOptions.map((option, index) => {
                                const isCorrectTarget = option === quizQuestionPhrase.native;
                                const isSelected = quizSelectedOption === option;
                                const hasUserSelectedAny = quizSelectedOption !== null;

                                let optionStyle = styles.quizOptionBtn;
                                let optionTxtStyle = styles.quizOptionBtnTxt;

                                if (hasUserSelectedAny) {
                                  if (isCorrectTarget) {
                                    // Highlights the correct answer in glowing emerald
                                    optionStyle = [styles.quizOptionBtn, styles.quizOptionCorrectBtn];
                                    optionTxtStyle = [styles.quizOptionBtnTxt, styles.quizOptionCorrectBtnTxt];
                                  } else if (isSelected) {
                                    // Highlights the incorrect tapped answer in red
                                    optionStyle = [styles.quizOptionBtn, styles.quizOptionIncorrectBtn];
                                    optionTxtStyle = [styles.quizOptionBtnTxt, styles.quizOptionIncorrectBtnTxt];
                                  } else {
                                    // Demotes other unselected options
                                    optionStyle = [styles.quizOptionBtn, styles.quizOptionDisabledBtn];
                                    optionTxtStyle = [styles.quizOptionBtnTxt, styles.quizOptionDisabledBtnTxt];
                                  }
                                }

                                return (
                                  <TouchableOpacity
                                    key={index}
                                    style={optionStyle}
                                    disabled={hasUserSelectedAny}
                                    onPress={async () => {
                                      const isCorrect = isCorrectTarget;
                                      setQuizSelectedOption(option);
                                      setQuizIsCorrect(isCorrect);
                                      setQuizScore(prev => ({
                                        correct: prev.correct + (isCorrect ? 1 : 0),
                                        total: prev.total + 1
                                      }));

                                      try {
                                        await Haptics.notificationAsync(
                                          isCorrect 
                                            ? Haptics.NotificationFeedbackType.Success 
                                            : Haptics.NotificationFeedbackType.Error
                                        );
                                      } catch(e){}

                                      if (isCorrect) {
                                        speakText(`Correct! It matches.`);
                                        // Wait a tiny bit and speak in native locale so they hear the word
                                        setTimeout(() => {
                                          speakText(quizQuestionPhrase.native, { language: currentCourse.locale });
                                        }, 800);
                                      } else {
                                        speakText(`Incorrect choice. Try another.`);
                                      }
                                    }}
                                    activeOpacity={0.7}
                                  >
                                    <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', width: '100%' }}>
                                      <Text style={optionTxtStyle}>{option}</Text>
                                      {hasUserSelectedAny && isCorrectTarget && (
                                        <Text style={{ fontSize: 9, color: '#00E676', fontWeight: '950' }}>✓ CORRECT</Text>
                                      )}
                                      {hasUserSelectedAny && isSelected && !isCorrectTarget && (
                                        <Text style={{ fontSize: 9, color: '#FF5252', fontWeight: '950' }}>✗ WRONG</Text>
                                      )}
                                    </View>
                                  </TouchableOpacity>
                                );
                              })}
                            </View>

                            {/* Post Match Explainer Context Panel */}
                            {quizSelectedOption !== null && (
                              <View style={[styles.quizFeedbackWrapper, quizIsCorrect ? styles.quizFeedbackCorrect : styles.quizFeedbackIncorrect]}>
                                <Text style={styles.quizFeedbackStatus}>
                                  {quizIsCorrect ? '🎉 EXCELLENT PRONUNCIATION SYNC' : '⚠️ MATCHING VARIANCE DETECTED'}
                                </Text>
                                <Text style={styles.quizFeedbackDetails}>
                                  The correct representation is <Text style={{ fontWeight: 'bold', color: '#FFF' }}>{quizQuestionPhrase.native}</Text>.
                                </Text>
                                <Text style={styles.quizFeedbackRoman}>
                                  Phonetic romanization: <Text style={{ color: '#FF9800', fontWeight: '700' }}>"{quizQuestionPhrase.roman}"</Text>
                                </Text>

                                <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: 10 }}>
                                  <TouchableOpacity
                                    style={styles.quizFeedbackSpeakerBtn}
                                    onPress={async () => {
                                      try { await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); } catch(e){}
                                      speakText(quizQuestionPhrase.native, { language: currentCourse.locale, rate: 0.8 });
                                    }}
                                  >
                                    <Text style={styles.quizFeedbackSpeakerBtnTxt}>🔊 Play native accent</Text>
                                  </TouchableOpacity>

                                  <TouchableOpacity
                                    style={styles.quizNextBtn}
                                    onPress={async () => {
                                      try { await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium); } catch(e){}
                                      triggerNextQuizQuestion(selectedCourseId, selectedCategory);
                                    }}
                                  >
                                    <Text style={styles.quizNextBtnTxt}>Next Phrase ➔</Text>
                                  </TouchableOpacity>
                                </View>
                              </View>
                            )}
                          </View>
                        ) : (
                          <Text style={{ color: '#888', fontSize: 10, textAlign: 'center', margin: 16 }}>
                            Generating quiz questions... Please check that the selected filters contain phrases.
                          </Text>
                        )}
                      </View>
                    )}
                  </View>
                );
              })()}
            </View>

            {/* LINGUISTIC VERIFICATION FEEDBACK & REWARD HUB */}
            <View style={styles.rewardHubOuterContainer}>
              <TouchableOpacity
                style={styles.rewardHubHeaderToggle}
                onPress={async () => {
                  try {
                    await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Medium);
                    setIsAuditorHubExpanded(!isAuditorHubExpanded);
                    if (!isAuditorHubExpanded) {
                      speakText("Linguistic verification hub active. Submit local translation reviews to earn reward credits.");
                    }
                  } catch (err) {
                    setIsAuditorHubExpanded(!isAuditorHubExpanded);
                  }
                }}
                activeOpacity={0.8}
              >
                <View style={{ flexDirection: 'row', alignItems: 'center', flex: 1 }}>
                  <Text style={{ fontSize: 13, marginRight: 6 }}>💎</Text>
                  <View style={{ flex: 1 }}>
                    <Text style={styles.rewardHubTitleMain}>TRANSLATION AUDIT & REWARDS HUB</Text>
                    <Text style={styles.rewardHubSubtitleMain}>Verify translated results to earn API credit vouchers</Text>
                  </View>
                </View>
                <View style={[styles.rewardExpandBadge, isAuditorHubExpanded && styles.rewardExpandBadgeActive]}>
                  <Text style={styles.rewardExpandBadgeTxt}>{isAuditorHubExpanded ? '✕ CLOSE' : '🚀 VIEW AUDIT'}</Text>
                </View>
              </TouchableOpacity>

              {isAuditorHubExpanded && (
                <View style={styles.rewardHubExpandedWrapper}>
                  
                  {/* WALLET METRICS BOARD */}
                  <View style={styles.walletBoard}>
                    <Text style={styles.walletBoardTitle}>💎 MY ANUVEDHAI REWARDS WALLET</Text>
                    <View style={styles.walletMetricsRow}>
                      <View style={styles.walletMetricBox}>
                        <Text style={styles.walletCardAmount}>{walletCredits}</Text>
                        <Text style={styles.walletCardLabel}>Active Credits ❇️</Text>
                      </View>
                      <View style={styles.walletMetricBox}>
                        <Text style={[styles.walletCardAmount, { color: '#FF9800' }]}>{pendingPoints}</Text>
                        <Text style={styles.walletCardLabel}>Pending Points ⏳</Text>
                      </View>
                    </View>
                    <Text style={styles.walletPromoNote}>
                      Active Credits are redeemable for priority server routes and voice synthetics. Verify translations to unlock more credits.
                    </Text>
                  </View>

                  {/* WEEKLY EARNING PROGRESS CHART (RECHARTS STYLE) */}
                  <View style={styles.chartContainerOuter}>
                    <Text style={styles.chartHeaderTitle}>📊 RECHARTS-INSPIRED WEEKLY EARNING TREND</Text>
                    <Text style={styles.chartHeaderSubtitle}>
                      Interactive active reward credits velocity tracker (Tap column to inspect day)
                    </Text>

                    {/* Active tooltip block */}
                    {(() => {
                      // Find chosen or current day's earnings
                      const inspectDayName = selectedChartDay || (() => {
                        const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
                        return days[new Date().getDay()];
                      })();
                      const dayData = weeklyEarnings.find(d => d.day === inspectDayName) || weeklyEarnings[0];
                      const maxVal = Math.max(...weeklyEarnings.map(w => w.credits));

                      return (
                        <View style={styles.chartTooltipBox}>
                          <Text style={styles.chartTooltipDay}>{inspectDayName.toUpperCase()} DETAILS:</Text>
                          <Text style={styles.chartTooltipValue}>
                            ✨ {dayData.credits} Active Credits {inspectDayName === selectedChartDay ? '(Selected)' : '(Today)'}
                          </Text>
                          <Text style={styles.chartTooltipStatus}>
                            {dayData.credits >= maxVal ? '🔥 PEAK ACTIVE DAY' : '📈 PROGRESS LEVEL'}
                          </Text>
                        </View>
                      );
                    })()}

                    {/* Chart Core Grid */}
                    <View style={styles.chartGridFrame}>
                      {/* Grid Background Lines representing Recharts gridlines */}
                      <View style={styles.chartGridLineRow}>
                        <Text style={styles.chartYAxisLabel}>150</Text>
                        <View style={styles.chartGridLineDashed} />
                      </View>
                      <View style={styles.chartGridLineRow}>
                        <Text style={styles.chartYAxisLabel}>100</Text>
                        <View style={styles.chartGridLineDashed} />
                      </View>
                      <View style={styles.chartGridLineRow}>
                        <Text style={styles.chartYAxisLabel}>50</Text>
                        <View style={styles.chartGridLineDashed} />
                      </View>
                      <View style={styles.chartGridLineRow}>
                        <Text style={styles.chartYAxisLabel}>0</Text>
                        <View style={styles.chartGridLineSolid} />
                      </View>

                      {/* Bar columns container positioned absolutely over grid */}
                      <View style={styles.chartColumnsWrapper}>
                        {weeklyEarnings.map((item, index) => {
                          const maxScaleValue = 150;
                          const heightPct = Math.min(100, Math.max(8, (item.credits / maxScaleValue) * 100));
                          
                          const daysOfWeek = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
                          const isToday = item.day === daysOfWeek[new Date().getDay()];
                          const isSelected = item.day === selectedChartDay;

                          return (
                            <TouchableOpacity
                              key={index}
                              style={styles.chartColTouchable}
                              activeOpacity={0.8}
                              onPress={async () => {
                                try { await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); } catch(e){}
                                setSelectedChartDay(item.day);
                              }}
                            >
                              {/* Numerical count at top of bar */}
                              <Text style={[styles.chartBarValueText, (isToday || isSelected) && styles.chartBarValueTextHighlighted]}>
                                {item.credits}
                              </Text>

                              {/* Bar Pillar */}
                              <View style={styles.chartBarTrack}>
                                <View style={[
                                  styles.chartBarFill,
                                  { height: `${heightPct}%` },
                                  isToday && styles.chartBarFillToday,
                                  isSelected && styles.chartBarFillSelected
                                ]}>
                                  {/* Beautiful linear cap representing modern Recharts area stroke gradient */}
                                  <View style={styles.chartBarNeonCap} />
                                </View>
                              </View>

                              {/* X Axis Label */}
                              <Text style={[styles.chartXLabel, (isToday || isSelected) && styles.chartXLabelHighlighted]}>
                                {item.day}
                              </Text>
                            </TouchableOpacity>
                          );
                        })}
                      </View>
                    </View>

                    {/* Chart Legend Controls */}
                    <View style={styles.chartLegendRow}>
                      <View style={styles.chartLegendIndicator}>
                        <View style={[styles.chartLegendColor, { backgroundColor: '#00E676' }]} />
                        <Text style={styles.chartLegendText}>Standard Day</Text>
                      </View>
                      <View style={styles.chartLegendIndicator}>
                        <View style={[styles.chartLegendColor, { backgroundColor: '#00E5FF' }]} />
                        <Text style={styles.chartLegendText}>Today</Text>
                      </View>
                      <View style={styles.chartLegendIndicator}>
                        <View style={[styles.chartLegendColor, { backgroundColor: '#FF9800' }]} />
                        <Text style={styles.chartLegendText}>Selected</Text>
                      </View>
                    </View>
                  </View>

                  {/* FEEDBACK FORM */}
                  <Text style={styles.rewardsSectionTitle}>📝 SUBMIT TRANSLATION VERIFICATION REVIEW</Text>
                  <View style={styles.rewardsFormStyle}>
                    
                    {/* Choose Language */}
                    <Text style={styles.rewardsFormLabel}>Translated Language Module:</Text>
                    <View style={styles.rewardsLangSelectorRow}>
                      {LANGUAGE_COURSES.map((course) => {
                        const isSelected = fbLanguage === course.id;
                        return (
                          <TouchableOpacity
                            key={course.id}
                            style={[styles.rewardsLangChip, isSelected && styles.rewardsLangChipActive]}
                            onPress={async () => {
                              try { await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); } catch(e){}
                              setFbLanguage(course.id);
                            }}
                            activeOpacity={0.7}
                          >
                            <Text style={[styles.rewardsLangChipTxt, isSelected && styles.rewardsLangChipTxtActive]}>
                              {course.name}
                            </Text>
                          </TouchableOpacity>
                        );
                      })}
                    </View>

                    {/* Original Phrase */}
                    <Text style={styles.rewardsFormLabel}>Original English Sentence:</Text>
                    <TextInput
                      style={styles.rewardsTextInput}
                      placeholder="e.g., Where is the local hospital?"
                      placeholderTextColor="#4E5E75"
                      value={fbOriginal}
                      onChangeText={setFbOriginal}
                      keyboardAppearance="dark"
                      autoCapitalize="sentences"
                    />

                    {/* Translated text */}
                    <Text style={styles.rewardsFormLabel}>Translated Script Result:</Text>
                    <TextInput
                      style={styles.rewardsTextInput}
                      placeholder="e.g., स्थानीय अस्पताल कहाँ है?"
                      placeholderTextColor="#4E5E75"
                      value={fbTranslated}
                      onChangeText={setFbTranslated}
                      keyboardAppearance="dark"
                    />

                    {/* Quality feedback status indicator */}
                    <Text style={styles.rewardsFormLabel}>Is this translation accurate?</Text>
                    <View style={styles.rewardsOptionRadioRow}>
                      <TouchableOpacity
                        style={[styles.rewardsRadioBtn, fbIsAccurate === true && styles.rewardsRadioBtnActiveCorrect]}
                        onPress={async () => {
                          try { await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); } catch(e){}
                          setFbIsAccurate(true);
                        }}
                        activeOpacity={0.7}
                      >
                        <Text style={[styles.rewardsRadioBtnTxt, fbIsAccurate === true && styles.rewardsRadioBtnTxtActive]}>
                          ✅ Yes, Accurate
                        </Text>
                      </TouchableOpacity>

                      <TouchableOpacity
                        style={[styles.rewardsRadioBtn, fbIsAccurate === false && styles.rewardsRadioBtnActiveInaccurate]}
                        onPress={async () => {
                          try { await Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light); } catch(e){}
                          setFbIsAccurate(false);
                        }}
                        activeOpacity={0.7}
                      >
                        <Text style={[styles.rewardsRadioBtnTxt, fbIsAccurate === false && styles.rewardsRadioBtnTxtActive]}>
                          ❌ Error / Needs Fix
                        </Text>
                      </TouchableOpacity>
                    </View>

                    {/* Correction details if inaccurate */}
                    {fbIsAccurate === false && (
                      <View style={{ marginTop: 8 }}>
                        <Text style={styles.rewardsFormLabel}>Suggested Correction Script:</Text>
                        <TextInput
                          style={styles.rewardsTextInput}
                          placeholder="Please write the correct translated phrase..."
                          placeholderTextColor="#4E5E75"
                          value={fbCorrection}
                          onChangeText={setFbCorrection}
                          keyboardAppearance="dark"
                        />
                      </View>
                    )}

                    <TouchableOpacity
                      style={styles.rewardsSubmitBtn}
                      onPress={handleSubmitLinguisticReview}
                      activeOpacity={0.8}
                    >
                      <Text style={styles.rewardsSubmitBtnTxt}>SUBMIT AUDIT REPORT (+50 POINTS) ➔</Text>
                    </TouchableOpacity>
                  </View>

                  {/* SIMULATED AUDIT SECTION */}
                  <View style={styles.teamSimulatorSection}>
                    <View style={styles.teamSimulatorHeader}>
                      <Text style={styles.teamSimHeading}>⚡ SYSTEM COMPLIANCE TERMINAL (TEAM AUDIT SIMULATOR)</Text>
                      <TouchableOpacity
                        style={styles.teamSimVerifyBtn}
                        onPress={handleSimulateTeamLinguisticAudit}
                        activeOpacity={0.7}
                      >
                        <Text style={styles.teamSimVerifyBtnTxt}>Run Team Verification & Audit</Text>
                      </TouchableOpacity>
                    </View>
                    <Text style={styles.teamSimDescTxt}>
                      Normally, the AnuVedhai linguistic team audits reports within 24 hours. Press the button above to simulate the team instantly reviewing and approving your pending feedback logs.
                    </Text>
                  </View>

                  {/* LATEST USER FEEDBACK LOGS */}
                  <Text style={styles.rewardsSectionTitle}>📋 MY VERIFICATION HISTORY</Text>
                  {feedbackHistory.length === 0 ? (
                    <Text style={{ color: '#5F6E84', fontSize: 8.5, textAlign: 'center', padding: 12 }}>
                      No verification logs submitted yet in this session.
                    </Text>
                  ) : (
                    feedbackHistory.map((item) => (
                      <View key={item.id} style={styles.feedbackLogCard}>
                        <View style={styles.feedbackLogCardHeader}>
                          <View style={styles.feedbackLanguageBadge}>
                            <Text style={styles.feedbackLanguageBadgeTxt}>{item.language.toUpperCase()}</Text>
                          </View>
                          <Text style={styles.feedbackLogTime}>{item.timestamp}</Text>
                        </View>

                        <Text style={styles.fbLogPhraseText}>
                          Original: <Text style={{ color: '#FFF' }}>"{item.originalText}"</Text>
                        </Text>
                        <Text style={styles.fbLogPhraseText}>
                          Translated: <Text style={{ color: '#FFF' }}>"{item.translatedText}"</Text>
                        </Text>

                        {item.userCorrection ? (
                          <Text style={styles.fbLogPhraseTextSmall}>
                            Suggested Fix: <Text style={{ color: '#FF9800', fontStyle: 'italic' }}>"{item.userCorrection}"</Text>
                          </Text>
                        ) : null}

                        <View style={styles.feedbackItemFooter}>
                          <View style={[
                            styles.fbStatusIndicator,
                            item.verificationStatus === 'Pending Verification' ? styles.fbStatusPending : styles.fbStatusApproved
                          ]}>
                            <Text style={styles.fbStatusIndicatorText}>
                              {item.verificationStatus === 'Pending Verification' ? '⏳ PENDING AUDIT' : '❇️ VERIFIED & DISBURSED'}
                            </Text>
                          </View>
                          <Text style={styles.fbStatusIndicatorPoints}>
                            {item.verificationStatus === 'Pending Verification' ? '+50 Points Pending' : '+50 Credits Added'}
                          </Text>
                        </View>
                      </View>
                    ))
                  )}

                </View>
              )}
            </View>

            {/* Custom NLP Output Segment */}
            <View style={styles.feedbackContainer}>
              <Text style={styles.feedbackHeading}>LOCAL SPEECH-NLP PARSER FEED</Text>
              <Text style={styles.feedbackBody}>{nlpFeedbackMsg}</Text>
            </View>

            {/* Dynamic Reverb/Cancel Rollback Dialog */}
            {lastCommandId && (
              <View style={styles.undoPrompt}>
                <Text style={styles.undoPromptText}>
                  Executed: <Text style={{ color: '#FF9800', fontWeight: 'bold' }}>{lastCommandLabel || lastCommandId}</Text>
                </Text>
                <TouchableOpacity
                  style={styles.undoBtn}
                  onPress={handleUndoLastAction}
                  activeOpacity={0.8}
                >
                  <Text style={styles.undoBtnText}>↩️ Cancel / Revert Last Action</Text>
                </TouchableOpacity>
              </View>
            )}

            {/* Dictation Simulation Field */}
            <View style={styles.manualVoiceWrapper}>
              <TextInput
                style={styles.manualVoiceInput}
                value={voiceInputText}
                onChangeText={(text) => {
                  setVoiceInputText(text);
                  if (isListeningSim && text.length > 0) {
                    setIsListeningSim(false);
                  }
                }}
                onSubmitEditing={() => {
                  handleExecuteVoiceCommand(voiceInputText);
                  setVoiceInputText('');
                }}
                placeholder="Type spoken sentence ('reload web', 'go to dialogue')..."
                placeholderTextColor="#7F7F7F"
                keyboardAppearance="dark"
              />
              <TouchableOpacity
                style={styles.sendVoiceBtn}
                onPress={() => {
                  handleExecuteVoiceCommand(voiceInputText);
                  setVoiceInputText('');
                }}
              >
                <Text style={styles.sendVoiceTxt}>Speak 🗣️</Text>
              </TouchableOpacity>
            </View>

            {/* Quick Presets Keyboard */}
            <Text style={styles.presetHeading}>💡 PRESETS (VOICE SIMULATOR TRUNKS)</Text>
            <View style={styles.presetsGrid}>
              {[
                { phrase: 'go to dialogue mode', icon: '💬 Dialogue' },
                { phrase: 'go to call translator', icon: '📞 Call Suite' },
                { phrase: 'play recent translation', icon: '🔊 Play Audio' },
                { phrase: 'refresh web app', icon: '🔄 Reload Web' },
                { phrase: 'clear offline cache', icon: '🧹 Clear Local' },
                { phrase: 'say help hands free', icon: '❓ Voice Helper' }
              ].map((btn, index) => (
                <TouchableOpacity
                  key={index}
                  style={styles.presetChip}
                  onPress={() => {
                    setVoiceInputText(btn.phrase);
                    handleExecuteVoiceCommand(btn.phrase);
                    setVoiceInputText('');
                  }}
                >
                  <Text style={styles.presetChipText}>{btn.icon}</Text>
                </TouchableOpacity>
              ))}
            </View>

            {/* Recent Voice Commands Audit History (Scrollable) */}
            <View style={styles.recentCommandsHeaderRowOuter}>
              <Text style={styles.recentCommandsHeading}>📋 RECENT VOICE COMMANDS LOG</Text>
              {lastAutoSaved && (
                <Text style={styles.autoSaveBadge}>✓ Auto-Saved {lastAutoSaved}</Text>
              )}
            </View>
            <View style={styles.recentCommandsContainer}>
              {recentCommands.length === 0 ? (
                <View style={styles.noCommandsWrapper}>
                  <Text style={styles.noCommandsText}>No voice commands executed in this session yet.</Text>
                </View>
              ) : (
                <ScrollView 
                  style={styles.recentCommandsScroll}
                  contentContainerStyle={styles.recentCommandsScrollContent}
                  nestedScrollEnabled={true}
                >
                  {recentCommands.map((cmd) => (
                    <View key={cmd.id} style={styles.recentCommandItem}>
                      <View style={styles.recentCommandHeader}>
                        <Text style={styles.recentCommandTime}>[{cmd.timestamp}]</Text>
                        <View style={[
                          styles.recentStatusBadge,
                          cmd.status === 'Executed' && styles.statusBadgeExecuted,
                          cmd.status === 'Reverted' && styles.statusBadgeReverted,
                          cmd.status === 'Unrecognized' && styles.statusBadgeUnrecognized,
                          cmd.status === 'Error' && styles.statusBadgeError
                        ]}>
                          <Text style={[
                            styles.statusBadgeTxt,
                            cmd.status === 'Executed' && styles.badgeTxtExecuted,
                            cmd.status === 'Reverted' && styles.badgeTxtReverted,
                            cmd.status === 'Unrecognized' && styles.badgeTxtUnrecognized,
                            cmd.status === 'Error' && styles.badgeTxtError
                          ]}>
                            {cmd.status === 'Executed' ? 'EXECUTED' : cmd.status === 'Reverted' ? 'REVERTED ↩️' : cmd.status === 'Unrecognized' ? 'MISSED ❓' : 'ERROR ❌'}
                          </Text>
                        </View>
                      </View>
                      <View style={styles.recentCommandDetails}>
                        <Text style={styles.recentPhraseTxt} numberOfLines={1}>Phrase: "{cmd.phrase}"</Text>
                        <Text style={styles.recentMatchTxt} numberOfLines={1}>Action: {cmd.matchedText}</Text>
                      </View>
                    </View>
                  ))}
                </ScrollView>
              )}
            </View>
          </View>
        )}
      </View>
    </SafeAreaView>
  );
}

// Simple internal helper component
const Spacer = ({ height }: { height: number }) => <View style={{ height }} />;

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#1A1A1A',
  },
  webWrapper: {
    flex: 1,
    overflow: 'hidden',
  },
  webview: {
    flex: 1,
    backgroundColor: '#121212',
  },
  nativeLoader: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: '#121212',
    justifyContent: 'center',
    alignItems: 'center',
  },
  loaderTxt: {
    color: '#E0E0E0',
    fontSize: 13,
    fontWeight: '600',
    letterSpacing: 0.5,
  },
  errorContainer: {
    flex: 1,
    backgroundColor: '#121212',
    padding: 16,
  },
  offlineHeader: {
    alignItems: 'center',
    backgroundColor: '#1E1E1E',
    borderRadius: 16,
    padding: 20,
    borderWidth: 1,
    borderColor: 'rgba(255, 152, 0, 0.12)',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.25,
    shadowRadius: 4,
    elevation: 6,
    marginBottom: 16,
  },
  emojiIcon: {
    fontSize: 40,
    marginBottom: 8,
  },
  errorTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#FFFFFF',
    marginBottom: 6,
  },
  errorSubtitle: {
    fontSize: 12,
    color: '#B0B0B0',
    textAlign: 'center',
    lineHeight: 18,
    marginBottom: 16,
  },
  buttonRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },
  retryButton: {
    backgroundColor: '#FF9800',
    paddingVertical: 10,
    paddingHorizontal: 20,
    borderRadius: 20,
    marginRight: 10,
  },
  retryText: {
    color: '#000000',
    fontSize: 11,
    fontWeight: 'bold',
    letterSpacing: 0.5,
  },
  clearCacheBtn: {
    backgroundColor: 'rgba(255, 255, 255, 0.08)',
    paddingVertical: 10,
    paddingHorizontal: 16,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.15)',
  },
  clearCacheTxt: {
    color: '#E0E0E0',
    fontSize: 11,
    fontWeight: 'bold',
    letterSpacing: 0.5,
  },
  historySection: {
    flex: 1,
    backgroundColor: '#1A1A1A',
    borderRadius: 16,
    padding: 12,
  },
  historyHeading: {
    fontSize: 13,
    fontWeight: 'bold',
    color: '#A0A0A0',
    marginBottom: 12,
    letterSpacing: 0.5,
  },
  emptyCacheState: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 16,
  },
  emptyCacheText: {
    fontSize: 12,
    color: '#707070',
    textAlign: 'center',
    lineHeight: 18,
  },
  transcriptCard: {
    backgroundColor: '#222222',
    borderRadius: 12,
    padding: 14,
    marginBottom: 12,
    borderLeftWidth: 3,
    borderLeftColor: '#FF9800',
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  langLabel: {
    color: '#FF9800',
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 0.5,
  },
  timeLabel: {
    color: '#707070',
    fontSize: 9,
  },
  cardSection: {
    marginVertical: 4,
  },
  textHeading: {
    color: '#808080',
    fontSize: 8,
    fontWeight: '700',
    marginBottom: 2,
    letterSpacing: 0.5,
  },
  textBody: {
    color: '#D0D0D0',
    fontSize: 12,
    lineHeight: 16,
  },
  lineDivider: {
    height: 1,
    backgroundColor: 'rgba(255, 255, 255, 0.05)',
    marginVertical: 6,
  },
  transHeading: {
    color: '#FFA726',
    fontSize: 8,
    fontWeight: '700',
    marginBottom: 2,
    letterSpacing: 0.5,
  },
  transBody: {
    color: '#FFFFFF',
    fontSize: 13,
    fontWeight: '600',
    lineHeight: 17,
  },
  floatingMicBtn: {
    position: 'absolute',
    bottom: 24,
    right: 20,
    backgroundColor: '#FF9800',
    paddingVertical: 12,
    paddingHorizontal: 20,
    borderRadius: 30,
    flexDirection: 'row',
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.35,
    shadowRadius: 5,
    elevation: 8,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.15)',
  },
  floatingMicText: {
    color: '#000000',
    fontWeight: '900',
    fontSize: 12,
    letterSpacing: 0.5,
  },
  voiceCommandPanel: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    backgroundColor: '#1E1E1E',
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    padding: 16,
    borderTopWidth: 2,
    borderTopColor: '#FF9800',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: -4 },
    shadowOpacity: 0.4,
    shadowRadius: 6,
    elevation: 12,
  },
  voiceHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  voiceTitleRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  pulseCircle: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: '#4CAF50',
    marginRight: 8,
  },
  voiceTitle: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '800',
    letterSpacing: 0.5,
  },
  closeVoiceBtn: {
    backgroundColor: 'rgba(255, 255, 255, 0.05)',
    paddingVertical: 4,
    paddingHorizontal: 8,
    borderRadius: 6,
  },
  closeVoiceTxt: {
    color: '#FF5252',
    fontSize: 10,
    fontWeight: '700',
  },
  soundwaveModule: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    height: 46,
    marginBottom: 12,
    position: 'relative',
  },
  soundbar: {
    width: 3,
    backgroundColor: '#FF9800',
    marginHorizontal: 2.5,
    borderRadius: 1.5,
  },
  thresholdLine: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 5,
  },
  thresholdDashedLine: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: 1,
    borderColor: '#E91E63',
    borderWidth: 0.6,
    borderRadius: 0.5,
    borderStyle: 'dashed',
    opacity: 0.8,
  },
  thresholdPill: {
    backgroundColor: '#E91E63',
    borderRadius: 8,
    paddingHorizontal: 7,
    paddingVertical: 1,
    zIndex: 10,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.4,
    shadowRadius: 1.5,
    elevation: 3,
  },
  thresholdLabel: {
    color: '#FFFFFF',
    fontSize: 7.5,
    fontWeight: '900',
    letterSpacing: 0.3,
  },
  sensitivityContainer: {
    backgroundColor: '#161616',
    borderRadius: 12,
    padding: 10,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.05)',
  },
  sensitivityHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  sensitivityTitle: {
    fontSize: 8.5,
    color: '#808080',
    fontWeight: '800',
    letterSpacing: 0.5,
  },
  sensitivityBadge: {
    color: '#FF9800',
    fontSize: 8,
    fontWeight: '900',
    backgroundColor: 'rgba(255, 152, 0, 0.1)',
    paddingVertical: 2,
    paddingHorizontal: 6,
    borderRadius: 4,
  },
  sliderInteractiveRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginVertical: 10,
  },
  adjustBtn: {
    backgroundColor: 'rgba(255, 255, 255, 0.06)',
    borderRadius: 14,
    paddingVertical: 5,
    paddingHorizontal: 8,
    minWidth: 58,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.03)',
  },
  adjustBtnTxt: {
    color: '#D0D0D0',
    fontSize: 9,
    fontWeight: '700',
  },
  sliderTrackWrapper: {
    flex: 1,
    height: 24,
    marginHorizontal: 12,
    justifyContent: 'center',
    position: 'relative',
  },
  sliderBaseLine: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: 3,
    backgroundColor: '#2A2A2A',
    borderRadius: 1.5,
  },
  sliderActiveFill: {
    position: 'absolute',
    left: 0,
    height: 3,
    backgroundColor: '#FF9800',
    borderRadius: 1.5,
  },
  snapDotTouchArea: {
    position: 'absolute',
    top: 0,
    bottom: 0,
    width: 24,
    marginLeft: -12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  snapDotCircle: {
    width: 7,
    height: 7,
    borderRadius: 3.5,
    backgroundColor: '#444444',
    borderWidth: 1.5,
    borderColor: '#161616',
  },
  snapDotActive: {
    backgroundColor: '#FF9800',
    width: 10,
    height: 10,
    borderRadius: 5,
    borderColor: '#FFFFFF',
    borderWidth: 1,
  },
  snapDotText: {
    color: '#606060',
    fontSize: 7,
    fontWeight: '700',
    position: 'absolute',
    bottom: -4,
  },
  snapDotTextActive: {
    color: '#FFFFFF',
    fontWeight: 'bold',
  },
  sensitivityDesc: {
    color: '#8A8A8A',
    fontSize: 8.5,
    lineHeight: 12,
    fontStyle: 'italic',
  },
  feedbackContainer: {
    backgroundColor: '#121212',
    borderRadius: 10,
    padding: 10,
    marginBottom: 12,
    borderLeftWidth: 2,
    borderLeftColor: '#4CAF50',
  },
  feedbackHeading: {
    fontSize: 8,
    color: '#808080',
    fontWeight: '800',
    marginBottom: 4,
    letterSpacing: 0.5,
  },
  feedbackBody: {
    color: '#E0E0E0',
    fontSize: 11,
    lineHeight: 15,
  },
  undoPrompt: {
    backgroundColor: 'rgba(255, 152, 0, 0.06)',
    borderRadius: 10,
    padding: 10,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: 'rgba(255, 152, 0, 0.2)',
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  undoPromptText: {
    color: '#CCCCCC',
    fontSize: 10,
    flex: 1,
    marginRight: 6,
  },
  undoBtn: {
    backgroundColor: '#FF9800',
    paddingVertical: 6,
    paddingHorizontal: 10,
    borderRadius: 8,
  },
  undoBtnText: {
    color: '#000000',
    fontSize: 9,
    fontWeight: '900',
  },
  manualVoiceWrapper: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 12,
  },
  manualVoiceInput: {
    flex: 1,
    backgroundColor: '#121212',
    color: '#FFFFFF',
    borderRadius: 20,
    paddingVertical: 8,
    paddingHorizontal: 16,
    fontSize: 12,
    marginRight: 10,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.06)',
  },
  sendVoiceBtn: {
    backgroundColor: '#FF9800',
    paddingVertical: 8,
    paddingHorizontal: 14,
    borderRadius: 20,
  },
  sendVoiceTxt: {
    color: '#000000',
    fontSize: 11,
    fontWeight: 'bold',
  },
  presetHeading: {
    fontSize: 8,
    color: '#808080',
    fontWeight: '800',
    marginBottom: 6,
    letterSpacing: 0.5,
  },
  presetsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
  },
  presetChip: {
    backgroundColor: '#2A2A2A',
    borderRadius: 14,
    paddingVertical: 5,
    paddingHorizontal: 10,
    marginBottom: 6,
    width: '48%',
    alignItems: 'center',
  },
  presetChipText: {
    color: '#E0E0E0',
    fontSize: 10,
    fontWeight: '600',
  },
  recentCommandsHeaderRowOuter: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 10,
    marginBottom: 6,
  },
  recentCommandsHeading: {
    fontSize: 8,
    color: '#808080',
    fontWeight: '800',
    letterSpacing: 0.5,
  },
  autoSaveBadge: {
    fontSize: 7.5,
    color: '#4CAF50',
    fontWeight: '900',
    backgroundColor: 'rgba(76, 175, 80, 0.1)',
    paddingVertical: 2,
    paddingHorizontal: 6,
    borderRadius: 4,
    letterSpacing: 0.3,
  },
  recentCommandsContainer: {
    backgroundColor: '#121212',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.04)',
    maxHeight: 125,
    overflow: 'hidden',
  },
  noCommandsWrapper: {
    padding: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  noCommandsText: {
    color: '#666666',
    fontSize: 9.5,
    textAlign: 'center',
    fontStyle: 'italic',
  },
  recentCommandsScroll: {
    paddingHorizontal: 10,
  },
  recentCommandsScrollContent: {
    paddingVertical: 6,
  },
  recentCommandItem: {
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255, 255, 255, 0.04)',
    paddingVertical: 6,
    marginBottom: 4,
  },
  recentCommandHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 3,
  },
  recentCommandTime: {
    color: '#808080',
    fontSize: 8,
    fontWeight: '600',
    fontFamily: Platform.OS === 'ios' ? 'Courier New' : 'monospace',
  },
  recentStatusBadge: {
    paddingVertical: 1.5,
    paddingHorizontal: 5,
    borderRadius: 4,
  },
  statusBadgeExecuted: {
    backgroundColor: 'rgba(76, 175, 80, 0.1)',
  },
  statusBadgeReverted: {
    backgroundColor: 'rgba(255, 152, 0, 0.12)',
  },
  statusBadgeUnrecognized: {
    backgroundColor: 'rgba(128, 128, 128, 0.15)',
  },
  statusBadgeError: {
    backgroundColor: 'rgba(244, 67, 54, 0.15)',
  },
  statusBadgeTxt: {
    fontSize: 7,
    fontWeight: '900',
    letterSpacing: 0.3,
  },
  badgeTxtExecuted: {
    color: '#4CAF50',
  },
  badgeTxtReverted: {
    color: '#FF9800',
  },
  badgeTxtUnrecognized: {
    color: '#909090',
  },
  badgeTxtError: {
    color: '#FF5252',
  },
  recentCommandDetails: {
    paddingLeft: 4,
  },
  recentPhraseTxt: {
    color: '#E0E0E0',
    fontSize: 10,
    fontWeight: '700',
    lineHeight: 13,
  },
  recentMatchTxt: {
    color: '#8A8A8A',
    fontSize: 9,
    marginTop: 1,
  },
  wakeWordSensorDock: {
    position: 'absolute',
    bottom: 16,
    right: 16,
    left: 16,
    backgroundColor: '#1E1E1E',
    borderRadius: 16,
    padding: 12,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.06)',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 6 },
    shadowOpacity: 0.4,
    shadowRadius: 8,
    elevation: 8,
  },
  radarMonitorRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  radarLed: {
    width: 6,
    height: 6,
    borderRadius: 3,
    marginRight: 6,
  },
  radarLedOn: {
    backgroundColor: '#4CAF50',
    shadowColor: '#4CAF50',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 1,
    shadowRadius: 4,
  },
  radarLedOff: {
    backgroundColor: '#555555',
  },
  radarMonitorText: {
    color: '#D0D0D0',
    fontSize: 8.5,
    fontWeight: '800',
    letterSpacing: 0.4,
  },
  wakeSimRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  wakeSimInput: {
    flex: 1,
    height: 32,
    backgroundColor: '#121212',
    color: '#FFFFFF',
    borderRadius: 8,
    paddingHorizontal: 10,
    fontSize: 10.5,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.08)',
    marginRight: 6,
  },
  wakeSimBtn: {
    backgroundColor: 'rgba(255, 152, 0, 0.15)',
    height: 32,
    paddingHorizontal: 12,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: 'rgba(255, 152, 0, 0.25)',
  },
  wakeSimBtnTxt: {
    color: '#FF9800',
    fontSize: 10,
    fontWeight: '700',
  },
  wakeSimDisabledBox: {
    backgroundColor: '#141414',
    borderRadius: 8,
    padding: 8,
    marginBottom: 8,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.03)',
  },
  wakeSimDisabledTxt: {
    color: '#666666',
    fontSize: 8.5,
    lineHeight: 11,
    textAlign: 'center',
  },
  floatingMicBtnInline: {
    backgroundColor: '#FF9800',
    borderRadius: 12,
    paddingVertical: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  wakeWordSettingsContainer: {
    backgroundColor: '#161616',
    borderRadius: 12,
    padding: 10,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.05)',
  },
  wakeWordSettingsHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  wakeWordSectionTitle: {
    fontSize: 8.5,
    color: '#808080',
    fontWeight: '800',
    letterSpacing: 0.5,
  },
  radarToggleBtn: {
    borderRadius: 4,
    paddingVertical: 2,
    paddingHorizontal: 6,
  },
  radarToggleBtnActive: {
    backgroundColor: 'rgba(76, 175, 80, 0.15)',
  },
  radarToggleBtnInactive: {
    backgroundColor: 'rgba(255, 255, 255, 0.08)',
  },
  radarToggleTxt: {
    color: '#4CAF50',
    fontSize: 7.5,
    fontWeight: '900',
    letterSpacing: 0.3,
  },
  wakeWordQuickConfigRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  wakeWordLabel: {
    color: '#8A8A8A',
    fontSize: 10,
    fontWeight: '600',
  },
  wakeWordWordBadge: {
    backgroundColor: 'rgba(255, 152, 0, 0.1)',
    borderRadius: 6,
    paddingVertical: 2,
    paddingHorizontal: 8,
    borderWidth: 1,
    borderColor: 'rgba(255, 152, 0, 0.25)',
  },
  wakeWordWordBadgeTxt: {
    color: '#FF9800',
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 0.4,
  },
  calibratingWrapper: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255, 61, 0, 0.08)',
    borderRadius: 8,
    paddingVertical: 8,
    borderWidth: 1,
    borderColor: 'rgba(255, 61, 0, 0.2)',
    marginBottom: 8,
  },
  calibratingStateTxt: {
    color: '#FF3D00',
    fontSize: 8.5,
    fontWeight: '900',
    letterSpacing: 0.3,
  },
  wakeInputActionsWrapper: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 8,
  },
  wakeWordActionInput: {
    flex: 1,
    height: 32,
    backgroundColor: '#121212',
    color: '#FFFFFF',
    borderRadius: 8,
    paddingHorizontal: 10,
    fontSize: 11,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.08)',
    marginRight: 6,
  },
  calibrateMicBtn: {
    backgroundColor: '#FF5252',
    height: 32,
    paddingHorizontal: 10,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  calibrateMicBtnTxt: {
    color: '#FFFFFF',
    fontSize: 10,
    fontWeight: '800',
  },
  quickSelectWordLabel: {
    color: '#808080',
    fontSize: 8,
    fontWeight: '800',
    marginBottom: 4,
    letterSpacing: 0.3,
  },
  quickWordPresetChipsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  quickWordChip: {
    backgroundColor: '#222222',
    borderRadius: 10,
    paddingVertical: 4,
    width: '23%',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.04)',
  },
  quickWordChipActive: {
    backgroundColor: 'rgba(255, 152, 0, 0.15)',
    borderColor: '#FF9800',
  },
  quickWordChipTxt: {
    color: '#8A8A8A',
    fontSize: 9,
    fontWeight: '600',
  },
  quickWordChipTxtActive: {
    color: '#FF9800',
    fontWeight: '900',
  },
  batterySettingsContainer: {
    backgroundColor: '#161616',
    borderRadius: 12,
    padding: 10,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.05)',
  },
  batterySettingsHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  batteryIcon: {
    fontSize: 11,
    marginRight: 4,
  },
  batterySectionTitle: {
    fontSize: 8.5,
    color: '#808080',
    fontWeight: '800',
    letterSpacing: 0.5,
  },
  batterySaveActiveBtn: {
    backgroundColor: 'rgba(0, 230, 118, 0.12)',
  },
  batteryInfoRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 8,
    backgroundColor: '#121212',
    padding: 8,
    borderRadius: 8,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.03)',
  },
  batteryMetricsColumn: {
    flexDirection: 'column',
  },
  batteryStatLabel: {
    color: '#666666',
    fontSize: 8,
    fontWeight: '800',
    letterSpacing: 0.3,
  },
  batteryStatValue: {
    color: '#FFFFFF',
    fontSize: 10.5,
    fontWeight: '700',
    marginTop: 2,
  },
  batteryStatusBadgeWrapper: {
    alignItems: 'flex-end',
  },
  ecoBadgeOn: {
    backgroundColor: 'rgba(0, 230, 118, 0.15)',
    borderWidth: 1,
    borderColor: 'rgba(0, 230, 118, 0.3)',
    borderRadius: 6,
    paddingVertical: 3,
    paddingHorizontal: 8,
  },
  ecoBadgeOnTxt: {
    color: '#00E676',
    fontSize: 8,
    fontWeight: '900',
    letterSpacing: 0.4,
  },
  ecoBadgeOff: {
    backgroundColor: 'rgba(255, 255, 255, 0.06)',
    borderRadius: 6,
    paddingVertical: 3,
    paddingHorizontal: 8,
  },
  ecoBadgeOffTxt: {
    color: '#8A8A8A',
    fontSize: 8.5,
    fontWeight: '800',
  },
  simulatorTitleRow: {
    marginBottom: 4,
  },
  simulatorTitle: {
    color: '#808080',
    fontSize: 8,
    fontWeight: '800',
    letterSpacing: 0.3,
  },
  batteryPresetRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  batteryChip: {
    backgroundColor: '#222222',
    borderRadius: 8,
    paddingVertical: 4,
    width: '18%',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.04)',
  },
  batteryChipActive: {
    backgroundColor: 'rgba(76, 175, 80, 0.15)',
    borderColor: '#4CAF50',
  },
  batteryChipActiveLow: {
    backgroundColor: 'rgba(244, 67, 54, 0.15)',
    borderColor: '#F44336',
  },
  batteryChipTxt: {
    color: '#8A8A8A',
    fontSize: 9,
    fontWeight: '600',
  },
  batteryChipTxtActive: {
    color: '#4CAF50',
    fontWeight: '900',
  },
  frequencyFooterRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    borderTopWidth: 1,
    borderColor: 'rgba(255,255,255,0.04)',
    paddingTop: 6,
  },
  frequencyBox: {
    flexDirection: 'row',
    alignItems: 'center',
    width: '48%',
  },
  freqLabel: {
    color: '#666666',
    fontSize: 8,
    fontWeight: '700',
    marginRight: 4,
  },
  freqValue: {
    color: '#D0D0D0',
    fontSize: 8,
    fontWeight: '800',
  },
  courseOuterContainer: {
    backgroundColor: '#151b26', // Deep travel blue slate background
    borderRadius: 12,
    padding: 10,
    marginBottom: 12,
    borderWidth: 1,
    borderColor: 'rgba(0, 230, 255, 0.15)', // Futuristic sci-fi travel cyan accent
  },
  courseHeaderToggle: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  courseTitleMain: {
    fontSize: 9,
    color: '#00E5FF', // glowing cyan title
    fontWeight: '900',
    letterSpacing: 0.6,
  },
  courseSubtitleMain: {
    color: '#8A99AD',
    fontSize: 7.5,
    fontWeight: '600',
    marginTop: 1,
  },
  expandBadge: {
    backgroundColor: 'rgba(255, 255, 255, 0.05)',
    borderRadius: 6,
    paddingVertical: 4,
    paddingHorizontal: 8,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.08)',
  },
  expandBadgeActive: {
    backgroundColor: 'rgba(255, 152, 0, 0.15)',
    borderColor: '#FF9800',
  },
  expandBadgeTxt: {
    color: '#FFFFFF',
    fontSize: 8,
    fontWeight: '900',
    letterSpacing: 0.3,
  },
  expandedCourseWrapper: {
    marginTop: 10,
    borderTopWidth: 1,
    borderColor: 'rgba(255,255,255,0.06)',
    paddingTop: 10,
  },
  courseTabContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 10,
  },
  courseTabButton: {
    backgroundColor: '#0E131F',
    borderRadius: 8,
    paddingVertical: 5,
    width: '24%',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.04)',
  },
  courseTabButtonActive: {
    backgroundColor: 'rgba(0, 229, 255, 0.1)',
    borderColor: '#00E5FF',
  },
  courseTabTxt: {
    color: '#6F7E94',
    fontSize: 8.5,
    fontWeight: '700',
  },
  courseTabTxtActive: {
    color: '#00E5FF',
    fontWeight: '900',
  },
  courseLanguageMetaRow: {
    backgroundColor: '#0E131F',
    padding: 8,
    borderRadius: 8,
    marginBottom: 8,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.02)',
  },
  nativeLanguageTitle: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '900',
  },
  englishLanguageTitle: {
    color: '#00E5FF',
    fontSize: 10,
    fontWeight: '800',
  },
  languageSpeakerBtn: {
    backgroundColor: 'rgba(255, 255, 255, 0.06)',
    borderRadius: 10,
    paddingVertical: 2,
    paddingHorizontal: 8,
    marginLeft: 6,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.08)',
  },
  languageSpeakerEmoji: {
    color: '#FFFFFF',
    fontSize: 8,
    fontWeight: '800',
  },
  languageQuickFact: {
    color: '#8A99AD',
    fontSize: 8,
    lineHeight: 11,
    marginTop: 4,
    fontWeight: '500',
  },
  scheduleTimelineBox: {
    backgroundColor: 'rgba(255, 152, 0, 0.06)',
    borderWidth: 1,
    borderColor: 'rgba(255, 152, 0, 0.15)',
    borderRadius: 8,
    padding: 8,
    marginBottom: 8,
  },
  scheduleLabel: {
    color: '#FF9800',
    fontSize: 8,
    fontWeight: '900',
    letterSpacing: 0.4,
    marginBottom: 2,
  },
  scheduleValue: {
    color: '#D4E2F5',
    fontSize: 8,
    lineHeight: 11,
    fontWeight: '600',
  },
  categoryFilterRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  categoryFilterChip: {
    backgroundColor: '#0E131F',
    paddingVertical: 4,
    paddingHorizontal: 4,
    borderRadius: 6,
    width: '19%',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.04)',
  },
  categoryFilterChipActive: {
    backgroundColor: 'rgba(255, 152, 0, 0.12)',
    borderColor: '#FF9800',
  },
  categoryFilterChipTxt: {
    color: '#5F6E84',
    fontSize: 7,
    fontWeight: '800',
  },
  categoryFilterChipTxtActive: {
    color: '#FF9800',
    fontWeight: '900',
  },
  phrasesListingBox: {
    marginTop: 4,
  },
  phraseItemCard: {
    backgroundColor: '#0E131F',
    borderColor: 'rgba(255,255,255,0.03)',
    borderWidth: 1,
    borderRadius: 8,
    padding: 8,
    marginBottom: 6,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  phraseItemMainColumn: {
    flex: 1,
    marginRight: 6,
  },
  phraseCategoryTagWrapper: {
    backgroundColor: 'rgba(255, 255, 255, 0.04)',
    alignSelf: 'flex-start',
    borderRadius: 4,
    paddingHorizontal: 4,
    paddingVertical: 1,
    marginBottom: 3,
  },
  phraseCategoryTagTxt: {
    color: '#8A99AD',
    fontSize: 6,
    fontWeight: '900',
    letterSpacing: 0.3,
  },
  phraseEnglish: {
    color: '#A0AEC0',
    fontSize: 8,
    fontWeight: '500',
    marginBottom: 1,
  },
  phraseNative: {
    color: '#FFFFFF',
    fontSize: 11,
    fontWeight: '900',
    marginBottom: 2,
  },
  phraseRoman: {
    color: '#FF9800',
    fontSize: 8,
    fontStyle: 'italic',
    fontWeight: '600',
  },
  phraseSpeakerButton: {
    backgroundColor: 'rgba(0, 229, 255, 0.08)',
    borderColor: 'rgba(0, 229, 255, 0.2)',
    borderWidth: 1,
    borderRadius: 8,
    paddingVertical: 6,
    paddingHorizontal: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  phraseSpeakerIcon: {
    color: '#00E5FF',
    fontSize: 8,
    fontWeight: '900',
  },
  subTabGroup: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    backgroundColor: '#0E131F',
    borderRadius: 8,
    padding: 3,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.03)',
  },
  subTabButton: {
    flex: 1,
    paddingVertical: 5,
    alignItems: 'center',
    borderRadius: 6,
  },
  subTabButtonActive: {
    backgroundColor: '#1E293B',
    borderWidth: 1,
    borderColor: 'rgba(0, 229, 255, 0.2)',
  },
  subTabTxt: {
    color: '#6F7E94',
    fontSize: 8.5,
    fontWeight: '800',
  },
  subTabTxtActive: {
    color: '#00E5FF',
    fontWeight: '900',
  },
  quizBoxOuter: {
    backgroundColor: '#0E131F',
    borderRadius: 10,
    padding: 10,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.02)',
  },
  quizHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
    paddingBottom: 6,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(255,255,255,0.05)',
  },
  quizSectionHeader: {
    fontSize: 8,
    color: '#8A99AD',
    fontWeight: '900',
    letterSpacing: 0.4,
  },
  quizScoreBadge: {
    fontSize: 8,
    color: '#00E676',
    fontWeight: '900',
    marginRight: 8,
  },
  quizResetLink: {
    opacity: 0.8,
  },
  quizResetLinkText: {
    color: '#FF9800',
    fontSize: 8,
    fontWeight: '800',
  },
  quizCardInner: {
    marginTop: 4,
  },
  quizQuestionCard: {
    backgroundColor: '#161F30',
    borderRadius: 8,
    padding: 10,
    marginBottom: 10,
    borderWidth: 1,
    borderColor: 'rgba(0,229,255,0.1)',
  },
  quizQuestionLabel: {
    color: '#8A99AD',
    fontSize: 7.5,
    fontWeight: '600',
    marginBottom: 4,
  },
  quizQuestionText: {
    color: '#FFFFFF',
    fontSize: 12,
    fontWeight: '900',
    fontStyle: 'italic',
  },
  quizOptionsGroup: {
    marginBottom: 8,
  },
  quizOptionBtn: {
    backgroundColor: '#151b26',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.05)',
    borderRadius: 8,
    paddingVertical: 8,
    paddingHorizontal: 10,
    marginBottom: 6,
    flexDirection: 'row',
    alignItems: 'center',
  },
  quizOptionCorrectBtn: {
    backgroundColor: 'rgba(0, 230, 118, 0.12)',
    borderColor: '#00E676',
  },
  quizOptionIncorrectBtn: {
    backgroundColor: 'rgba(255, 82, 82, 0.12)',
    borderColor: '#FF5252',
  },
  quizOptionDisabledBtn: {
    backgroundColor: 'rgba(21, 27, 38, 0.4)',
    borderColor: 'rgba(255,255,255,0.01)',
  },
  quizOptionBtnTxt: {
    color: '#FFF',
    fontSize: 10,
    fontWeight: '700',
  },
  quizOptionCorrectBtnTxt: {
    color: '#00E676',
    fontWeight: '900',
  },
  quizOptionIncorrectBtnTxt: {
    color: '#FF5252',
    fontWeight: '900',
  },
  quizOptionDisabledBtnTxt: {
    color: '#4A5568',
  },
  quizFeedbackWrapper: {
    borderRadius: 8,
    padding: 10,
    marginTop: 4,
    borderWidth: 1,
  },
  quizFeedbackCorrect: {
    backgroundColor: 'rgba(0, 230, 118, 0.05)',
    borderColor: 'rgba(0, 230, 118, 0.15)',
  },
  quizFeedbackIncorrect: {
    backgroundColor: 'rgba(255, 152, 0, 0.05)',
    borderColor: 'rgba(255, 152, 0, 0.15)',
  },
  quizFeedbackStatus: {
    fontSize: 8,
    fontWeight: '900',
    letterSpacing: 0.4,
    marginBottom: 4,
    color: '#FF9800',
  },
  quizFeedbackDetails: {
    color: '#A0AEC0',
    fontSize: 8.5,
    marginBottom: 3,
  },
  quizFeedbackRoman: {
    color: '#D4E2F5',
    fontSize: 8.5,
    fontStyle: 'italic',
  },
  quizFeedbackSpeakerBtn: {
    backgroundColor: 'rgba(255,255,255,0.06)',
    borderRadius: 6,
    paddingVertical: 5,
    paddingHorizontal: 8,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.1)',
  },
  quizFeedbackSpeakerBtnTxt: {
    color: '#FFF',
    fontSize: 8,
    fontWeight: '800',
  },
  quizNextBtn: {
    backgroundColor: '#FF9800',
    borderRadius: 6,
    paddingVertical: 5,
    paddingHorizontal: 12,
  },
  quizNextBtnTxt: {
    color: '#000',
    fontSize: 8,
    fontWeight: '900',
  },
  loginPageContainer: {
    flex: 1,
    backgroundColor: '#070A13',
  },
  loginScrollContainer: {
    padding: 20,
    paddingTop: 45,
    paddingBottom: 60,
  },
  loginLogoSection: {
    alignItems: 'center',
    marginBottom: 25,
  },
  loginAppIcon: {
    fontSize: 34,
    marginBottom: 8,
  },
  loginAppName: {
    color: '#00E5FF',
    fontSize: 20,
    fontWeight: '900',
    letterSpacing: 0.5,
  },
  loginAppTagline: {
    color: '#647D9E',
    fontSize: 9.5,
    textAlign: 'center',
    marginTop: 4,
    fontWeight: '700',
  },
  loginCard: {
    backgroundColor: '#0F1524',
    borderWidth: 1,
    borderColor: 'rgba(0, 229, 255, 0.1)',
    borderRadius: 14,
    padding: 16,
  },
  loginCardTitle: {
    color: '#FFF',
    fontSize: 13,
    fontWeight: '800',
    marginBottom: 4,
  },
  loginCardDesc: {
    color: '#7C8FAD',
    fontSize: 9,
    lineHeight: 12,
    marginBottom: 16,
  },
  loginTabGroup: {
    flexDirection: 'row',
    backgroundColor: '#060910',
    borderRadius: 8,
    padding: 3,
    marginBottom: 14,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.03)',
  },
  loginTabBtn: {
    flex: 1,
    paddingVertical: 7,
    alignItems: 'center',
    borderRadius: 6,
  },
  loginTabBtnActive: {
    backgroundColor: '#162035',
    borderWidth: 1,
    borderColor: 'rgba(0, 229, 255, 0.2)',
  },
  loginTabBtnTxt: {
    color: '#6F7E94',
    fontSize: 10,
    fontWeight: '800',
  },
  loginTabBtnTxtActive: {
    color: '#00E5FF',
    fontWeight: '900',
  },
  loginInputWrapper: {
    marginBottom: 16,
    width: '100%',
  },
  loginInputLabel: {
    color: '#9BB1CF',
    fontSize: 9,
    fontWeight: '800',
    marginBottom: 6,
    textTransform: 'uppercase',
  },
  loginTextInputField: {
    backgroundColor: '#080C14',
    borderWidth: 1,
    borderColor: 'rgba(255, 255, 255, 0.08)',
    borderRadius: 8,
    color: '#FFF',
    fontSize: 11,
    paddingHorizontal: 12,
    paddingVertical: 9,
  },
  termsTitledBox: {
    backgroundColor: '#080C14',
    borderRadius: 10,
    borderWidth: 1,
    borderColor: 'rgba(255,152,0,0.15)',
    padding: 10,
    marginBottom: 14,
  },
  termsTitleText: {
    fontSize: 8.5,
    color: '#FF9800',
    fontWeight: '900',
    marginBottom: 6,
    letterSpacing: 0.3,
  },
  termsScrollBox: {
    height: 120,
  },
  termsBodyText: {
    color: '#A0AEC0',
    fontSize: 9,
    lineHeight: 13,
  },
  checkboxLine: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 18,
    paddingRight: 6,
  },
  checkboxSquare: {
    width: 16,
    height: 16,
    borderRadius: 4,
    borderWidth: 1.5,
    borderColor: '#7C8FAD',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 9,
    backgroundColor: '#080C14',
  },
  checkboxSquareChecked: {
    borderColor: '#00E676',
    backgroundColor: 'rgba(0, 230, 118, 0.1)',
  },
  checkboxTick: {
    color: '#00E676',
    fontSize: 9.5,
    fontWeight: '950',
  },
  checkboxLabel: {
    color: '#A0AEC0',
    fontSize: 9,
    flex: 1,
    lineHeight: 12,
  },
  loginSubmitBtn: {
    backgroundColor: '#00E5FF',
    borderRadius: 8,
    paddingVertical: 11,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#00E5FF',
    shadowOpacity: 0.1,
    shadowOffset: { width: 0, height: 4 },
    shadowRadius: 10,
  },
  loginSubmitBtnDisabled: {
    backgroundColor: '#1F2E3F',
    opacity: 0.6,
  },
  loginSubmitBtnTxt: {
    color: '#000',
    fontSize: 10.5,
    fontWeight: '900',
    letterSpacing: 0.5,
  },
  loginGlowingVisual: {
    position: 'absolute',
    top: -150,
    left: -150,
    width: 400,
    height: 400,
    borderRadius: 200,
    backgroundColor: 'rgba(0, 229, 255, 0.05)',
    zIndex: -1,
  },
  rewardHubOuterContainer: {
    backgroundColor: '#111827',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: 'rgba(0, 229, 255, 0.08)',
    marginTop: 14,
    marginBottom: 12,
    overflow: 'hidden',
  },
  rewardHubHeaderToggle: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: 12,
    backgroundColor: 'rgba(0, 229, 255, 0.03)',
  },
  rewardHubTitleMain: {
    color: '#00E5FF',
    fontSize: 10,
    fontWeight: '950',
    letterSpacing: 0.3,
  },
  rewardHubSubtitleMain: {
    color: '#8A99AD',
    fontSize: 8,
    marginTop: 1,
  },
  rewardExpandBadge: {
    backgroundColor: '#1F2937',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.05)',
  },
  rewardExpandBadgeActive: {
    backgroundColor: '#374151',
    borderColor: '#00E5FF',
  },
  rewardExpandBadgeTxt: {
    color: '#D1D5DB',
    fontSize: 7.5,
    fontWeight: '900',
  },
  rewardHubExpandedWrapper: {
    padding: 12,
    borderTopWidth: 1,
    borderColor: 'rgba(255,255,255,0.03)',
    backgroundColor: '#0F1524',
  },
  walletBoard: {
    backgroundColor: '#080C14',
    borderWidth: 1,
    borderColor: 'rgba(0, 229, 255, 0.1)',
    borderRadius: 10,
    padding: 10,
    marginBottom: 14,
  },
  walletBoardTitle: {
    color: '#D1E2F5',
    fontSize: 8.5,
    fontWeight: '900',
    marginBottom: 8,
    letterSpacing: 0.2,
  },
  walletMetricsRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 8,
  },
  walletMetricBox: {
    flex: 1,
    backgroundColor: '#111822',
    borderRadius: 6,
    padding: 8,
    alignItems: 'center',
    marginHorizontal: 3,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.02)',
  },
  walletCardAmount: {
    color: '#00E676',
    fontSize: 16,
    fontWeight: '950',
  },
  walletCardLabel: {
    color: '#7F8FA4',
    fontSize: 7.5,
    fontWeight: '800',
    marginTop: 2,
  },
  walletPromoNote: {
    fontSize: 7.5,
    color: '#637A93',
    lineHeight: 11,
    fontStyle: 'italic',
    textAlign: 'center',
  },
  rewardsSectionTitle: {
    fontSize: 8.5,
    color: '#9CCAFF',
    fontWeight: '900',
    marginBottom: 8,
    letterSpacing: 0.3,
  },
  rewardsFormStyle: {
    backgroundColor: '#080C14',
    borderRadius: 10,
    padding: 10,
    marginBottom: 14,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.02)',
  },
  rewardsFormLabel: {
    color: '#8CA1C4',
    fontSize: 8,
    fontWeight: '800',
    marginBottom: 4,
    marginTop: 6,
  },
  rewardsLangSelectorRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    marginBottom: 6,
  },
  rewardsLangChip: {
    backgroundColor: '#111827',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
    marginRight: 5,
    marginBottom: 5,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.04)',
  },
  rewardsLangChipActive: {
    backgroundColor: 'rgba(0, 229, 255, 0.1)',
    borderColor: '#00E5FF',
  },
  rewardsLangChipTxt: {
    color: '#9CA3AF',
    fontSize: 7.5,
    fontWeight: '800',
  },
  rewardsLangChipTxtActive: {
    color: '#00E5FF',
    fontWeight: '900',
  },
  rewardsTextInput: {
    backgroundColor: '#0F1524',
    borderRadius: 6,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.06)',
    paddingHorizontal: 8,
    paddingVertical: 6,
    color: '#FFF',
    fontSize: 9.5,
    marginBottom: 8,
  },
  rewardsOptionRadioRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 6,
  },
  rewardsRadioBtn: {
    flex: 1,
    backgroundColor: '#151b26',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.05)',
    borderRadius: 6,
    paddingVertical: 6,
    alignItems: 'center',
    marginHorizontal: 2,
  },
  rewardsRadioBtnActiveCorrect: {
    backgroundColor: 'rgba(0, 230, 118, 0.1)',
    borderColor: '#00E676',
  },
  rewardsRadioBtnActiveInaccurate: {
    backgroundColor: 'rgba(255, 82, 82, 0.1)',
    borderColor: '#FF5252',
  },
  rewardsRadioBtnTxt: {
    color: '#9BB1CF',
    fontSize: 8,
    fontWeight: '800',
  },
  rewardsRadioBtnTxtActive: {
    color: '#FFF',
    fontWeight: '950',
  },
  rewardsSubmitBtn: {
    backgroundColor: '#00E5FF',
    borderRadius: 6,
    paddingVertical: 8,
    alignItems: 'center',
    marginTop: 8,
  },
  rewardsSubmitBtnTxt: {
    color: '#000',
    fontSize: 8.5,
    fontWeight: '900',
    letterSpacing: 0.3,
  },
  teamSimulatorSection: {
    backgroundColor: '#1E1B18',
    borderRadius: 8,
    padding: 10,
    marginBottom: 14,
    borderWidth: 1,
    borderColor: 'rgba(255,152,0,0.15)',
  },
  teamSimulatorHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 4,
  },
  teamSimHeading: {
    color: '#FF9800',
    fontSize: 8,
    fontWeight: '950',
  },
  teamSimVerifyBtn: {
    backgroundColor: '#FF9800',
    borderRadius: 4,
    paddingHorizontal: 6,
    paddingVertical: 3,
  },
  teamSimVerifyBtnTxt: {
    color: '#000',
    fontSize: 7.5,
    fontWeight: '900',
  },
  teamSimDescTxt: {
    fontSize: 7.5,
    color: '#A0AEC0',
    lineHeight: 11,
  },
  feedbackLogCard: {
    backgroundColor: '#080C14',
    borderRadius: 8,
    padding: 8,
    marginBottom: 6,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.03)',
  },
  feedbackLogCardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 4,
  },
  feedbackLanguageBadge: {
    backgroundColor: '#1F2937',
    borderRadius: 3,
    paddingHorizontal: 4,
    paddingVertical: 1.5,
  },
  feedbackLanguageBadgeTxt: {
    color: '#E5E7EB',
    fontSize: 6.5,
    fontWeight: '900',
  },
  feedbackLogTime: {
    color: '#4B5563',
    fontSize: 7,
    fontWeight: '800',
  },
  fbLogPhraseText: {
    color: '#9CA3AF',
    fontSize: 8,
    lineHeight: 11,
  },
  fbLogPhraseTextSmall: {
    color: '#9CA3AF',
    fontSize: 7.5,
    lineHeight: 10,
    marginTop: 2,
  },
  feedbackItemFooter: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: 6,
    paddingTop: 4,
    borderTopWidth: 1,
    borderColor: 'rgba(255,255,255,0.03)',
  },
  fbStatusIndicator: {
    borderRadius: 3,
    paddingHorizontal: 4,
    paddingVertical: 2,
  },
  fbStatusPending: {
    backgroundColor: 'rgba(255, 152, 0, 0.1)',
  },
  fbStatusApproved: {
    backgroundColor: 'rgba(0, 230, 118, 0.1)',
  },
  fbStatusIndicatorText: {
    color: '#FFF',
    fontSize: 6.5,
    fontWeight: '950',
  },
  fbStatusIndicatorPoints: {
    color: '#00E676',
    fontSize: 7.5,
    fontWeight: '900',
  },
  chartContainerOuter: {
    backgroundColor: '#080C14',
    borderWidth: 1,
    borderColor: 'rgba(0, 229, 255, 0.08)',
    borderRadius: 10,
    padding: 10,
    marginBottom: 14,
  },
  chartHeaderTitle: {
    color: '#00E5FF',
    fontSize: 9,
    fontWeight: '950',
    letterSpacing: 0.3,
  },
  chartHeaderSubtitle: {
    color: '#7F8FA4',
    fontSize: 7.5,
    marginTop: 1,
    marginBottom: 10,
  },
  chartTooltipBox: {
    backgroundColor: '#111827',
    borderRadius: 6,
    padding: 8,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.05)',
    marginBottom: 10,
  },
  chartTooltipDay: {
    color: '#8A99AD',
    fontSize: 7,
    fontWeight: '900',
    marginBottom: 2,
  },
  chartTooltipValue: {
    color: '#FFF',
    fontSize: 9,
    fontWeight: '950',
  },
  chartTooltipStatus: {
    color: '#00E5FF',
    fontSize: 6.5,
    fontWeight: '800',
    marginTop: 2,
    letterSpacing: 0.2,
  },
  chartGridFrame: {
    height: 120,
    position: 'relative',
    justifyContent: 'space-between',
    paddingLeft: 22,
    paddingRight: 6,
    marginBottom: 8,
  },
  chartGridLineRow: {
    flexDirection: 'row',
    alignItems: 'center',
    height: 10,
  },
  chartYAxisLabel: {
    color: '#4B5563',
    fontSize: 7.5,
    fontWeight: '900',
    width: 20,
    textAlign: 'right',
    marginRight: 6,
  },
  chartGridLineDashed: {
    flex: 1,
    height: 1,
    borderStyle: 'dashed',
    borderWidth: 0.5,
    borderColor: 'rgba(255,255,255,0.06)',
  },
  chartGridLineSolid: {
    flex: 1,
    height: 1,
    backgroundColor: 'rgba(255,255,255,0.15)',
  },
  chartColumnsWrapper: {
    position: 'absolute',
    top: 0,
    bottom: 0,
    left: 22,
    right: 6,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-end',
    height: 120,
    paddingBottom: 4,
  },
  chartColTouchable: {
    flex: 1,
    alignItems: 'center',
    height: '100%',
    justifyContent: 'flex-end',
  },
  chartBarValueText: {
    color: '#5F6E84',
    fontSize: 7,
    fontWeight: '800',
    marginBottom: 2,
  },
  chartBarValueTextHighlighted: {
    color: '#FFF',
    fontWeight: '950',
  },
  chartBarTrack: {
    width: 14,
    height: 70,
    backgroundColor: 'rgba(255,255,255,0.02)',
    borderRadius: 3,
    overflow: 'hidden',
    justifyContent: 'flex-end',
  },
  chartBarFill: {
    width: '100%',
    backgroundColor: 'rgba(0, 230, 118, 0.35)',
    borderRadius: 3,
  },
  chartBarFillToday: {
    backgroundColor: 'rgba(0, 229, 255, 0.45)',
  },
  chartBarFillSelected: {
    backgroundColor: 'rgba(255, 152, 0, 0.45)',
  },
  chartBarNeonCap: {
    height: 3,
    backgroundColor: '#00E676',
    borderTopLeftRadius: 3,
    borderTopRightRadius: 3,
  },
  chartXLabel: {
    color: '#5F6E84',
    fontSize: 7.5,
    fontWeight: '800',
    marginTop: 6,
  },
  chartXLabelHighlighted: {
    color: '#FFF',
    fontWeight: '950',
  },
  chartLegendRow: {
    flexDirection: 'row',
    justifyContent: 'center',
    marginTop: 6,
  },
  chartLegendIndicator: {
    flexDirection: 'row',
    alignItems: 'center',
    marginHorizontal: 8,
  },
  chartLegendColor: {
    width: 6,
    height: 6,
    borderRadius: 3,
    marginRight: 4,
  },
  chartLegendText: {
    color: '#7C8FAD',
    fontSize: 7,
    fontWeight: '800',
  },
});
