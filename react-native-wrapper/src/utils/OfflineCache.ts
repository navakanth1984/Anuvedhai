import AsyncStorage from '@react-native-async-storage/async-storage';

export interface CachedTranslation {
  id: string;
  originalText: string;
  translatedText: string;
  sourceLang: string;
  targetLang: string;
  timestamp: string;
}

const CACHE_KEY = '@anuvedhai_recent_transcripts';
const MAX_LIMIT = 50; // Keep space optimized on user device

/**
 * Persists an entire list of updated turns/transcripts received from the frontend
 */
export async function saveTranscriptsCache(turns: CachedTranslation[]): Promise<boolean> {
  try {
    const trimmed = turns.slice(0, MAX_LIMIT);
    await AsyncStorage.setItem(CACHE_KEY, JSON.stringify(trimmed));
    return true;
  } catch (error) {
    console.error('Error saving transcripts to offline cache:', error);
    return false;
  }
}

/**
 * Appends a single new translation turn to the existing cache pool
 */
export async function appendToTranscriptsCache(turn: Omit<CachedTranslation, 'timestamp'>): Promise<CachedTranslation[]> {
  try {
    const current = await getTranscriptsCache();
    // Prevent duplicate entries
    if (current.some(item => item.id === turn.id)) {
      return current;
    }
    
    const newEntry: CachedTranslation = {
      ...turn,
      timestamp: new Date().toISOString()
    };
    
    const updated = [newEntry, ...current].slice(0, MAX_LIMIT);
    await AsyncStorage.setItem(CACHE_KEY, JSON.stringify(updated));
    return updated;
  } catch (error) {
    console.error('Error appending turn to offline cache:', error);
    return [];
  }
}

/**
 * Retrieves the stored chat history off disk
 */
export async function getTranscriptsCache(): Promise<CachedTranslation[]> {
  try {
    const raw = await AsyncStorage.getItem(CACHE_KEY);
    if (!raw) return [];
    return JSON.parse(raw) as CachedTranslation[];
  } catch (error) {
    console.error('Error loading transcripts from offline cache:', error);
    return [];
  }
}

/**
 * Wipes out all stored history on request
 */
export async function clearTranscriptsCache(): Promise<boolean> {
  try {
    await AsyncStorage.removeItem(CACHE_KEY);
    return true;
  } catch (error) {
    console.error('Error wiping transcripts cache:', error);
    return false;
  }
}
