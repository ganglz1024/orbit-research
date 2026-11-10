import type { LocalDocument, DocumentMeta } from '../types'

export function readLocal<T>(key: string, fallback: T): T {
  try { return JSON.parse(localStorage.getItem(key) || 'null') ?? fallback } catch { return fallback }
}
export function writeLocal(key: string, value: unknown) {
  localStorage.setItem(key, JSON.stringify(value))
}
let database: Promise<IDBDatabase> | undefined
function db() {
  if (!database) database = new Promise((resolve, reject) => {
    const req = indexedDB.open('orbit-research', 1)
    req.onupgradeneeded = () => req.result.createObjectStore('documents', { keyPath: 'id' })
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => { database = undefined; reject(req.error) }
  })
  return database
}
export async function saveDocument(document: LocalDocument) {
  const database = await db()
  return new Promise<void>((resolve, reject) => {
    const tx = database.transaction('documents', 'readwrite')
    tx.objectStore('documents').put(document)
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
    tx.onabort = () => reject(tx.error)
  })
}
export async function getDocument(id: string): Promise<LocalDocument | undefined> {
  const database = await db()
  return new Promise((resolve, reject) => {
    const req = database.transaction('documents').objectStore('documents').get(id)
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
}
export async function listDocuments(): Promise<DocumentMeta[]> {
  const database = await db()
  return new Promise((resolve, reject) => {
    const req = database.transaction('documents').objectStore('documents').getAll()
    req.onsuccess = () => resolve(req.result.map(({ id, name, pageCount, createdAt, demo }: LocalDocument) => ({ id, name, pageCount, createdAt, demo })).sort((a: DocumentMeta,b: DocumentMeta) => b.createdAt.localeCompare(a.createdAt)))
    req.onerror = () => reject(req.error)
  })
}
export async function deleteDocument(id:string){
  const database=await db()
  return new Promise<void>((resolve,reject)=>{
    const tx=database.transaction('documents','readwrite')
    tx.objectStore('documents').delete(id)
    tx.oncomplete=()=>resolve()
    tx.onerror=()=>reject(tx.error)
    tx.onabort=()=>reject(tx.error)
  })
}
export function downloadBlob(content: BlobPart, name: string, type = 'application/octet-stream') {
  const url = URL.createObjectURL(new Blob([content], { type }))
  const a = document.createElement('a'); a.href = url; a.download = name; a.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
