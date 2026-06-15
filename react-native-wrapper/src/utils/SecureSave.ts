import * as SecureStore from 'expo-secure-store';
import { CachedTranslation, getTranscriptsCache, saveTranscriptsCache } from './OfflineCache';

const SECURE_AUTOSAVE_KEY = 'anuvedhai_secure_autosave_transcripts';

/**
 * Saves transcripts state securely to SecureStore
 */
export async function autoSaveToSecureStore(transcripts: CachedTranslation[]): Promise<boolean> {
  try {
    if (!transcripts || transcripts.length === 0) {
      return false;
    }
    const dataString = JSON.stringify(transcripts);
    await SecureStore.setItemAsync(SECURE_AUTOSAVE_KEY, dataString);
    console.log('[AutoSave] SecureStore auto-save successful. Count:', transcripts.length);
    return true;
  } catch (error) {
    console.error('[AutoSave] Failed to save transcripts to SecureStore:', error);
    return false;
  }
}

/**
 * Retrieves the auto-saved transcripts state from SecureStore
 */
export async function getAutoSavedTranscripts(): Promise<CachedTranslation[]> {
  try {
    const rawData = await SecureStore.getItemAsync(SECURE_AUTOSAVE_KEY);
    if (!rawData) return [];
    return JSON.parse(rawData) as CachedTranslation[];
  } catch (error) {
    console.error('[AutoSave] Failed to retrieve auto-saved transcripts:', error);
    return [];
  }
}

/**
 * Recovers and merges any SecureStore records into normal AsyncStorage memory on startup
 */
export async function recoverCrashedSessionState(): Promise<{ recoveredCount: number; alreadyMerged: boolean }> {
  try {
    const secureData = await getAutoSavedTranscripts();
    if (secureData.length === 0) {
      return { recoveredCount: 0, alreadyMerged: false };
    }

    const currentCache = await getTranscriptsCache();
    
    // Find items in SecureStore that do not exist in the current AsyncStorage cache
    const currentIds = new Set(currentCache.map(i => i.id));
    const missingItems = secureData.filter(item => !currentIds.has(item.id));

    if (missingItems.length > 0) {
      // Merge unique items, keeping the temporal order sorted
      const merged = [...missingItems, ...currentCache].slice(0, 50);
      await saveTranscriptsCache(merged);
      console.log(`[AutoSave] Restored ${missingItems.length} missing transcript entries from crashed session SecureStore.`);
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
    await SecureStore.deleteItemAsync(SECURE_AUTOSAVE_KEY);
    return true;
  } catch (error) {
    console.error('[AutoSave] Failed to clear SecureStore auto-save:', error);
    return false;
  }
}
