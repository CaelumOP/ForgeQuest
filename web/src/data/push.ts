import { db } from './db'
import { friendsApi } from './friends-api'

const FRIENDS_AUTH_KEY = 'friendsAuth'

const FIREBASE_CONFIG = {
  apiKey: 'AIzaSyAYOn6GJ3QafyDWgwPHHyB_8uysRf1uhyY',
  authDomain: 'forgequest-512022.firebaseapp.com',
  projectId: 'forgequest-512022',
  messagingSenderId: '787186946908',
  appId: '1:787186946908:web:ac357c6c1236990048e799',
}

const VAPID_KEY =
  'BC1SD5iDs9aA7QlsOuwFv_qkUH_75-fc4mUxmUNxxDG38PynNyZpnAN_E_Q3wfAF1-HX7ppdfLjUAD6HP5bl9ac'

interface StoredAuth {
  sessionToken?: string
  pushToken?: string | null
  [key: string]: unknown
}

export async function pushSupported(): Promise<boolean> {
  try {
    if (
      typeof window === 'undefined' ||
      typeof navigator === 'undefined' ||
      !('serviceWorker' in navigator) ||
      !('Notification' in window) ||
      !('PushManager' in window)
    ) {
      return false
    }
    const { isSupported } = await import('firebase/messaging')
    return await isSupported()
  } catch {
    return false
  }
}

export function pushPermission(): NotificationPermission | 'unsupported' {
  try {
    if (typeof Notification === 'undefined') return 'unsupported'
    return Notification.permission
  } catch {
    return 'unsupported'
  }
}

async function getFcmToken(): Promise<string | null> {
  try {
    const [{ getApp, getApps, initializeApp }, { getMessaging, getToken }] = await Promise.all([
      import('firebase/app'),
      import('firebase/messaging'),
    ])
    const app = getApps().length > 0 ? getApp() : initializeApp(FIREBASE_CONFIG)
    const messaging = getMessaging(app)
    const serviceWorkerRegistration = await navigator.serviceWorker.ready
    const token = await getToken(messaging, { vapidKey: VAPID_KEY, serviceWorkerRegistration })
    return token || null
  } catch {
    return null
  }
}

async function readStoredAuth(): Promise<StoredAuth | null> {
  try {
    const row = await db.kv.get(FRIENDS_AUTH_KEY)
    return (row?.value as StoredAuth | undefined) ?? null
  } catch {
    return null
  }
}

async function storePushToken(pushToken: string): Promise<void> {
  try {
    const current = await readStoredAuth()
    if (!current) return
    await db.kv.put({ key: FRIENDS_AUTH_KEY, value: { ...current, pushToken } })
  } catch {
    // ignore storage errors
  }
}

export async function enablePush(): Promise<boolean> {
  try {
    if (!(await pushSupported())) return false
    const perm = await Notification.requestPermission()
    if (perm !== 'granted') return false

    const token = await getFcmToken()
    if (!token) return false

    const auth = await readStoredAuth()
    if (auth?.sessionToken) {
      await friendsApi.putPush(auth.sessionToken, token)
      await storePushToken(token)
    }
    return true
  } catch {
    return false
  }
}

export async function syncPushToken(): Promise<void> {
  try {
    if (pushPermission() !== 'granted') return
    if (!(await pushSupported())) return

    const auth = await readStoredAuth()
    if (!auth?.sessionToken) return

    const token = await getFcmToken()
    if (!token || token === auth.pushToken) return

    await friendsApi.putPush(auth.sessionToken, token)
    await storePushToken(token)
  } catch {
    // Never let background push sync surface an unhandled rejection.
  }
}
