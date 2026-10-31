import { api } from './client'

interface PushConfig {
  enabled: boolean
  publicKey: string | null
  subscriptions: number
}

/**
 * Estado de Web Push en este dispositivo:
 * - unsupported: el navegador no lo soporta (en iPhone, solo con la app instalada en la pantalla de inicio)
 * - disabled: el servidor no tiene claves VAPID
 * - denied: el usuario bloqueó las notificaciones; solo se puede cambiar desde los ajustes del navegador
 * - off / on: se puede activar / ya está activado
 */
export type PushState = 'unsupported' | 'disabled' | 'denied' | 'off' | 'on'

export function isPushSupported(): boolean {
  return 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window
}

export async function getPushState(): Promise<PushState> {
  if (!isPushSupported()) {
    return 'unsupported'
  }
  const config = await api<PushConfig>('/api/push/config')
  if (!config.enabled) {
    return 'disabled'
  }
  if (Notification.permission === 'denied') {
    return 'denied'
  }
  const subscription = await currentSubscription()
  return subscription ? 'on' : 'off'
}

/** Pide permiso, suscribe el navegador y registra la suscripción en el servidor. */
export async function enablePush(): Promise<PushState> {
  const config = await api<PushConfig>('/api/push/config')
  if (!config.enabled || !config.publicKey) {
    return 'disabled'
  }
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') {
    return permission === 'denied' ? 'denied' : 'off'
  }
  const registration = await navigator.serviceWorker.ready
  const subscription = await registration.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: base64UrlToBytes(config.publicKey),
  })
  await api<void>('/api/push/subscriptions', { method: 'POST', body: JSON.stringify(subscription.toJSON()) })
  return 'on'
}

export async function disablePush(): Promise<PushState> {
  const subscription = await currentSubscription()
  if (subscription) {
    await api<void>('/api/push/subscriptions/unsubscribe', {
      method: 'POST',
      body: JSON.stringify({ endpoint: subscription.endpoint }),
    })
    await subscription.unsubscribe()
  }
  return 'off'
}

async function currentSubscription(): Promise<PushSubscription | null> {
  const registration = await navigator.serviceWorker.getRegistration()
  return registration ? registration.pushManager.getSubscription() : null
}

function base64UrlToBytes(value: string): Uint8Array<ArrayBuffer> {
  const base64 = (value + '='.repeat((4 - (value.length % 4)) % 4)).replace(/-/g, '+').replace(/_/g, '/')
  const binary = atob(base64)
  const bytes = new Uint8Array(new ArrayBuffer(binary.length))
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes
}
