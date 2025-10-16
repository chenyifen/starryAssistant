# WakeService Immediate Resume on ASR Completion

This note documents the changes introduced to fix the persistent `LISTENING` UI state and enable `WakeService` to resume listening immediately when ASR completes.

## Changes

- Added `onAsrCompleted()` to `WakeWordCallback` and `notifyAsrCompleted()` to `WakeWordCallbackManager` to broadcast ASR completion events.
- In `SenseVoiceInputDevice`, emit `notifyAsrCompleted()` in the `processAudio` `finally` block after setting the STT state to `Loaded`/`NotInitialized`. This guarantees a single completion signal per ASR session.
- In `WakeService`, registered an internal callback that:
  - Cancels the previously scheduled delayed resume runnable (fallback), and
  - Calls `resumeAudioRecordAfterASR()` immediately to resume wake listening.
- In `VoiceAssistantStateCoordinator`, when STT transitions back to `Loaded` after ASR, if the UI is still `LISTENING`, reset it to `IDLE` to avoid the UI getting stuck.

## Rationale

- ASR completion is the definitive moment when wake listening can safely resume without resource contention.
- Routing the completion through the existing wake callback manager avoids tight coupling between STT and WakeService.
- Resetting the UI on `SttState.Loaded` is a safe guard against rare paths where no `InputEvent.None` or `Final` causes the coordinator to transition out of `LISTENING`.

## Notes

- The delayed resume in `WakeService` remains as a fallback and is canceled upon ASR completion.
- `SenseVoiceInputDevice` finalization ensures completion is emitted even for short/invalid speech (where `InputEvent.None` is sent).