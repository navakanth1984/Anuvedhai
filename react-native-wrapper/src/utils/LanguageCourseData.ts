export interface CoursePhrase {
  id: string;
  english: string;
  native: string;
  roman: string;
  category: 'Essentials' | 'Transit & Hotels' | 'Dining & Social' | 'Emergency';
}

export interface LanguageCourse {
  id: string;
  name: string;
  nativeName: string;
  locale: string;
  quickFact: string;
  hoursSchedule: string;
  phrases: CoursePhrase[];
}

export const LANGUAGE_COURSES: LanguageCourse[] = [
  {
    id: 'hi',
    name: 'Hindi',
    nativeName: 'हिन्दी',
    locale: 'hi-IN',
    quickFact: 'Formally written in Devanagari script. Spoken by over 600 million travelers and locals across Indian cities.',
    hoursSchedule: '36-Hr Mastery Checklist: Hr 1–12 (Pronunciation/Etiquette) ➔ Hr 13–24 (Navigation & Tuk-Tuk fares) ➔ Hr 25–36 (Tasting local street cuisines & bargaining).',
    phrases: [
      {
        id: 'hi_1',
        english: 'Hello / Greetings',
        native: 'नमस्ते',
        roman: 'Namaste',
        category: 'Essentials'
      },
      {
        id: 'hi_2',
        english: 'Yes / No',
        native: 'हाँ / नहीं',
        roman: 'Haan / Nahi',
        category: 'Essentials'
      },
      {
        id: 'hi_3',
        english: 'Where is the metro/station?',
        native: 'स्टेशन कहाँ है?',
        roman: 'Station kahan hai?',
        category: 'Transit & Hotels'
      },
      {
        id: 'hi_4',
        english: 'Please help me',
        native: 'कृपया मेरी मदद करें',
        roman: 'Kripya meri madad karein',
        category: 'Emergency'
      },
      {
        id: 'hi_5',
        english: 'How much is this for?',
        native: 'यह कितने का है?',
        roman: 'Yeh kitne ka hai?',
        category: 'Dining & Social'
      },
      {
        id: 'hi_6',
        english: 'I want pure vegetarian food',
        native: 'मुझे शुद्ध शाकाहारी खाना चाहिए',
        roman: 'Mujhe shuddh shakahari khana chahiye',
        category: 'Dining & Social'
      },
      {
        id: 'hi_7',
        english: 'Call the doctor immediately',
        native: 'तुरंत डॉक्टर को बुलाएं',
        roman: 'Turant doctor ko bulayein',
        category: 'Emergency'
      }
    ]
  },
  {
    id: 'es',
    name: 'Spanish',
    nativeName: 'Español',
    locale: 'es-ES',
    quickFact: 'Global phonetic language. Extremely dynamic haptic accents across Madrid, Barcelona, and the Americas.',
    hoursSchedule: '36-Hr Mastery Checklist: Hr 1–12 (Salutations & Dynamic Verbs) ➔ Hr 13–24 (Inquiring timetables & taxis) ➔ Hr 25–36 (Ordering tapas & asking for check).',
    phrases: [
      {
        id: 'es_1',
        english: 'Nice to meet you',
        native: 'Mucho gusto',
        roman: 'Moo-choh goos-toh',
        category: 'Essentials'
      },
      {
        id: 'es_2',
        english: 'Where is the washroom?',
        native: '¿Dónde está el baño?',
        roman: 'Don-deh es-tah el bah-nyoh',
        category: 'Transit & Hotels'
      },
      {
        id: 'es_3',
        english: 'Can you bring me the bill?',
        native: '¿Me trae la cuenta, por favor?',
        roman: 'Meh trah-eh lah kwen-tah por fah-vohr',
        category: 'Dining & Social'
      },
      {
        id: 'es_4',
        english: 'Stop, please!',
        native: '¡Para, por favor!',
        roman: 'Pah-rah, por fah-vohr',
        category: 'Emergency'
      },
      {
        id: 'es_5',
        english: 'Speak slower, please',
        native: 'Hable más despacio, por favor',
        roman: 'Ah-bleh mahs des-pah-syoh por fah-vohr',
        category: 'Essentials'
      },
      {
        id: 'es_6',
        english: 'Do you accept card payments?',
        native: '¿Aceptan tarjeta?',
        roman: 'Ah-sep-tan tar-heh-tah',
        category: 'Dining & Social'
      }
    ]
  },
  {
    id: 'fr',
    name: 'French',
    nativeName: 'Français',
    locale: 'fr-FR',
    quickFact: 'Renowned language of culinary arts and diplomacy. Absolute basic courtesy markers (Bonjour, S\'il vous plaît) trigger local assistance.',
    hoursSchedule: '36-Hr Mastery Checklist: Hr 1–12 (Pristine politeness formulas) ➔ Hr 13–24 (Train terminals & walking streets) ➔ Hr 25–36 (Café etiquettes & requesting dishes).',
    phrases: [
      {
        id: 'fr_1',
        english: 'Hello / Good morning',
        native: 'Bonjour, s\'il vous plaît',
        roman: 'Bon-zhoor, seel voo-pleh',
        category: 'Essentials'
      },
      {
        id: 'fr_2',
        english: 'Excuse me',
        native: 'Pardon, excusez-moi',
        roman: 'Par-dohn, ex-kyoo-zay mwah',
        category: 'Essentials'
      },
      {
        id: 'fr_3',
        english: 'Where is the hotel lobby?',
        native: 'Où se trouve le hall de l\'hôtel?',
        roman: 'Oo suh troov luh hohl duh lo-tel',
        category: 'Transit & Hotels'
      },
      {
        id: 'fr_4',
        english: 'This is delicious!',
        native: 'C\'est délicieux !',
        roman: 'Seh day-lee-syoo',
        category: 'Dining & Social'
      },
      {
        id: 'fr_5',
        english: 'I need an ambulance immediately',
        native: 'J\'ai besoin d\'une ambulance immédiatement',
        roman: 'Zheh buh-zwan doon am-byoo-lahns ee-may-dee-yat-mahn',
        category: 'Emergency'
      },
      {
        id: 'fr_6',
        english: 'Thank you very much',
        native: 'Merci beaucoup',
        roman: 'Mair-see boh-coo',
        category: 'Essentials'
      }
    ]
  },
  {
    id: 'ja',
    name: 'Japanese',
    nativeName: '日本語',
    locale: 'ja-JP',
    quickFact: 'High precision respect forms (Keigo). Proper inflection of minor phrases shows deep devotion to the hosting culture.',
    hoursSchedule: '36-Hr Mastery Checklist: Hr 1–12 (Kowtow & greeting nuances) ➔ Hr 13–24 (Subway station maps & bullet directions) ➔ Hr 25–36 (Izakaya ordering & paying at counters).',
    phrases: [
      {
        id: 'ja_1',
        english: 'I am pleased to meet you',
        native: 'はじめまして',
        roman: 'Hajimemashite',
        category: 'Essentials'
      },
      {
        id: 'ja_2',
        english: 'Excuse me / Sorry',
        native: 'すみません',
        roman: 'Sumimasen',
        category: 'Essentials'
      },
      {
        id: 'ja_3',
        english: 'Where is the station?',
        native: '駅はどこですか？',
        roman: 'Eki wa doko desu ka?',
        category: 'Transit & Hotels'
      },
      {
        id: 'ja_4',
        english: 'Thank you for your service',
        native: 'ありがとうございます',
        roman: 'Arigatou gozaimasu',
        category: 'Essentials'
      },
      {
        id: 'ja_5',
        english: 'Is raw fish served in this dish?',
        native: 'これは生魚ですか？',
        roman: 'Kore wa nama zakana desu ka?',
        category: 'Dining & Social'
      },
      {
        id: 'ja_6',
        english: 'Help! Please call police!',
        native: '助けて！警察を呼んでください！',
        roman: 'Tasukete! Keisatsu o yonde kudasai!',
        category: 'Emergency'
      }
    ]
  }
];
