import * as SecureStore from 'expo-secure-store';
import { Platform } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { CachedTranslation, getTranscriptsCache, saveTranscriptsCache } from './OfflineCache';

const SECURE_AUTOSAVE_KEY = 'anuvedhai_secure_autosave_transcripts';

/**
 * Checks if SecureStore is available on the current platform
 */
async function isSecureStoreAvailable(): Promise<boolean> {
  if (Platform.OS === 'web') {
    return false;
  }
  try {
    return await SecureStore.isAvailableAsync();
  } catch (error) {
    return false;
  }
}

/**
 * Saves transcripts state securely to SecureStore or falls back to AsyncStorage
 */
export async function autoSaveToSecureStore(transcripts: CachedTranslation[]): Promise<boolean> {
  try {
    if (!transcripts || transcripts.length === 0) {
      return false;
    }
    const dataString = JSON.stringify(transcripts);
    const useSecure = await isSecureStoreAvailable();
    if (useSecure) {
      await SecureStore.setItemAsync(SECURE_AUTOSAVE_KEY, dataString);
      console.log('[AutoSave] SecureStore auto-save successful. Count:', transcripts.length);
    } else {
      await AsyncStorage.setItem(SECURE_AUTOSAVE_KEY, dataString);
      console.log('[AutoSave] AsyncStorage fallback auto-save successful. Count:', transcripts.length);
    }
    return true;
  } catch (error) {
    console.error('[AutoSave] Failed to save transcripts:', error);
    return false;
  }
}

/**
 * Retrieves the auto-saved transcripts state from SecureStore/AsyncStorage
 */
export async function getAutoSavedTranscripts(): Promise<CachedTranslation[]> {
  try {
    let rawData: string | null = null;
    const useSecure = await isSecureStoreAvailable();
    if (useSecure) {
      rawData = await SecureStore.getItemAsync(SECURE_AUTOSAVE_KEY);
    } else {
      rawData = await AsyncStorage.getItem(SECURE_AUTOSAVE_KEY);
    }
    if (!rawData) return [];
    return JSON.parse(rawData) as CachedTranslation[];
  } catch (error) {
    console.error('[AutoSave] Failed to retrieve auto-saved transcripts:', error);
    return [];
  }
}

/**
 * Recovers and merges any SecureStore/AsyncStorage records into normal cache memory on startup
 */
export async function recoverCrashedSessionState(): Promise<{ recoveredCount: number; alreadyMerged: boolean }> {
  try {
    const secureData = await getAutoSavedTranscripts();
    if (secureData.length === 0) {
      return { recoveredCount: 0, alreadyMerged: false };
    }

    const currentCache = await getTranscriptsCache();
    
    // Find items in SecureStore/AsyncStorage that do not exist in the current cache
    const currentIds = new Set(currentCache.map(i => i.id));
    const missingItems = secureData.filter(item => !currentIds.has(item.id));

    if (missingItems.length > 0) {
      // Merge unique items, keeping the temporal order sorted
      const merged = [...missingItems, ...currentCache].slice(0, 50);
      await saveTranscriptsCache(merged);
      console.log(`[AutoSave] Restored ${missingItems.length} missing transcript entries from crashed session memory.`);
      return { recoveredCount: missingItems.length, alreadyMerged: false };
    }

    return { recoveredCount: 0, alreadyMerged: true };
  } catch (error) {
    console.error('[AutoSave] Crash recovery routine error:', error);
    return { recoveredCount: 0, alreadyMerged: false };
  }
}

/**
 * Clears any stored auto-save data (e.g. on manual clear/reset)
 */
export async function clearSecureAutoSave(): Promise<boolean> {
  try {
    const useSecure = await isSecureStoreAvailable();
    if (useSecure) {
      await SecureStore.deleteItemAsync(SECURE_AUTOSAVE_KEY);
    } else {
      await AsyncStorage.removeItem(SECURE_AUTOSAVE_KEY);
    }
    return true;
  } catch (error) {
    console.error('[AutoSave] Failed to clear auto-save:', error);
    return false;
  }
}
