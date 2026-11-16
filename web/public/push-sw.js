// Plain-JS service worker extension for FCM Web Push notifications (imported by Workbox).
const INBOX_URL = '/ForgeQuest/#/friends/inbox'

function readLangFromDb() {
  return new Promise((resolve) => {
    try {
      const req = indexedDB.open('forgequest')
      req.onerror = () => resolve(null)
      req.onupgradeneeded = () => {
        try { req.transaction?.abort() } catch {}
        resolve(null)
      }
      req.onsuccess = () => {
        const db = req.result
        try {
          if (!db.objectStoreNames.contains('kv')) {
            db.close()
            resolve(null)
            return
          }
          const tx = db.transaction('kv', 'readonly')
          const getReq = tx.objectStore('kv').get('lang')
          getReq.onsuccess = () => {
            const val = getReq.result && getReq.result.value
            db.close()
            resolve(val === 'ar' || val === 'en' ? val : null)
          }
          getReq.onerror = () => { db.close(); resolve(null) }
        } catch {
          try { db.close() } catch {}
          resolve(null)
        }
      }
    } catch {
      resolve(null)
    }
  })
}

self.addEventListener('push', (event) => {
  event.waitUntil((async () => {
    let payload = {}
    try { payload = event.data?.json() ?? {} } catch {}
    const d = payload?.data ?? payload ?? {}
    const type = d.type === 'activity' ? 'activity' : 'ping'
    const fromId = String(d.fromId || '')
    const rawName = String(d.name || 'ForgeQuest')
    const wrappedName = `\u2066${rawName}\u2069`

    const dbLang = await readLangFromDb()
    const navLang = (self.navigator?.language || '').toLowerCase().startsWith('ar') ? 'ar' : 'en'
    const lang = dbLang || navLang

    let title
    if (lang === 'ar') {
      title = type === 'activity'
        ? `أنهى ${wrappedName} تمرينه للتو 💪`
        : `${wrappedName}: يلا تمرّن! 💪`
    } else {
      title = type === 'activity'
        ? `${wrappedName} just finished a workout 💪`
        : `${wrappedName}: Do your workout! 💪`
    }

    await self.registration.showNotification(title, {
      icon: '/ForgeQuest/icon-192.png',
      badge: '/ForgeQuest/icon-192.png',
      tag: `${type}:${fromId}`,
      data: { url: INBOX_URL },
    })
  })())
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const targetUrl = event.notification?.data?.url || INBOX_URL
  event.waitUntil((async () => {
    const allClients = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    for (const client of allClients) {
      if (client.url.includes('/ForgeQuest/') && 'focus' in client) {
        await client.focus()
        if ('navigate' in client) {
          try { await client.navigate(targetUrl) } catch {}
        }
        return
      }
    }
    await self.clients.openWindow(targetUrl)
  })())
})
