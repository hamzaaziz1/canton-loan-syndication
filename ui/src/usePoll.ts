import { useCallback, useEffect, useRef, useState } from 'react'

/** Polls fn every intervalMs: the read model is eventually consistent, so the UI keeps asking. */
export function usePoll<T>(fn: () => Promise<T>, intervalMs = 2000) {
  const [data, setData] = useState<T>()
  const [error, setError] = useState<unknown>()
  const fnRef = useRef(fn)
  useEffect(() => {
    fnRef.current = fn
  })

  const refresh = useCallback(async () => {
    try {
      setData(await fnRef.current())
      setError(undefined)
    } catch (e) {
      setError(e)
    }
  }, [])

  useEffect(() => {
    void refresh()
    const id = setInterval(() => void refresh(), intervalMs)
    return () => clearInterval(id)
  }, [refresh, intervalMs])

  return { data, error, refresh }
}
