/** Coalesces refresh requests while ensuring an in-flight event is not lost. */
export function createRefreshQueue(refresh: () => Promise<unknown>) {
  let inFlight: Promise<unknown> | undefined;
  let trailingRequested = false;

  function request(): Promise<unknown> {
    if (inFlight) {
      trailingRequested = true;
      return inFlight;
    }

    inFlight = refresh().finally(() => {
      const shouldRefreshAgain = trailingRequested;
      trailingRequested = false;
      inFlight = undefined;
      if (shouldRefreshAgain) {
        // Start only after the current promise has settled. Multiple events
        // during the request are represented by this single trailing run.
        queueMicrotask(() => { void request(); });
      }
    });
    return inFlight;
  }

  return { request };
}
