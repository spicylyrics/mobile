//! Reads Windows' media sessions and writes one JSON line to stdout per session per change.
//!
//! Lines come from the four session events and from a 250 ms poll. A poll line is written only when
//! something in the session differs from the last line written for it, so the poll lines show how
//! often each player really moves its position.

use serde_json::{json, Value};
use std::collections::HashMap;
use std::hash::{Hash, Hasher};
use std::io::Write;
use std::sync::{Mutex, OnceLock};
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};
use windows::Foundation::TypedEventHandler;
use windows::Media::MediaPlaybackAutoRepeatMode as Repeat;
use windows::Media::Control::{
    GlobalSystemMediaTransportControlsSession as Session,
    GlobalSystemMediaTransportControlsSessionManager as Manager,
    GlobalSystemMediaTransportControlsSessionMediaProperties as MediaProperties,
    GlobalSystemMediaTransportControlsSessionPlaybackStatus as Status,
};
use windows::Storage::Streams::DataReader;
use windows::Win32::System::WinRT::{RoInitialize, RO_INIT_MULTITHREADED};

/// The cover file written for each track (app, title, artist, album), so a poll doesn't read it again.
static COVERS: OnceLock<Mutex<HashMap<String, String>>> = OnceLock::new();
/// Last line written per app, without its timestamps and event name.
static LAST: OnceLock<Mutex<HashMap<String, Value>>> = OnceLock::new();
static START: OnceLock<Instant> = OnceLock::new();

/// 100 ns ticks since 1601 (WinRT `DateTime`) to Unix ms.
fn ticks_to_unix_ms(ticks: i64) -> i64 {
    (ticks - 116_444_736_000_000_000) / 10_000
}

fn now_ms() -> i64 {
    SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_millis() as i64
}

fn status_name(s: Status) -> &'static str {
    match s {
        Status::Closed => "Closed",
        Status::Opened => "Opened",
        Status::Changing => "Changing",
        Status::Stopped => "Stopped",
        Status::Playing => "Playing",
        Status::Paused => "Paused",
        _ => "Unknown",
    }
}

fn repeat_name(r: Repeat) -> &'static str {
    match r {
        Repeat::None => "None",
        Repeat::Track => "Track",
        Repeat::List => "List",
        _ => "Unknown",
    }
}

/// The session's thumbnail written to a temp file named by its content, so the path changes when
/// the picture does. Read once per track, and again on a media event: some players send the new
/// title before the new picture.
fn cover(app: &str, media: &MediaProperties, refresh: bool) -> Option<String> {
    let text = |r: windows::core::Result<windows::core::HSTRING>| r.map(|s| s.to_string()).unwrap_or_default();
    let key = format!("{app}\x1f{}\x1f{}\x1f{}", text(media.Title()), text(media.Artist()), text(media.AlbumTitle()));
    let covers = COVERS.get_or_init(Default::default);
    if !refresh {
        if let Some(path) = covers.lock().unwrap().get(&key) {
            return Some(path.clone());
        }
    }
    let stream = media.Thumbnail().ok()?.OpenReadAsync().ok()?.get().ok()?;
    let size = stream.Size().ok()? as u32;
    if size == 0 {
        return None;
    }
    let reader = DataReader::CreateDataReader(&stream).ok()?;
    reader.LoadAsync(size).ok()?.get().ok()?;
    let mut bytes = vec![0u8; size as usize];
    reader.ReadBytes(&mut bytes).ok()?;
    let mut hasher = std::collections::hash_map::DefaultHasher::new();
    bytes.hash(&mut hasher);
    let dir = std::env::temp_dir().join("spicy-lyrics-covers");
    std::fs::create_dir_all(&dir).ok()?;
    let path = dir.join(format!("{:016x}.img", hasher.finish()));
    if !path.exists() {
        std::fs::write(&path, &bytes).ok()?;
    }
    let path = path.to_string_lossy().into_owned();
    covers.lock().unwrap().insert(key, path.clone());
    Some(path)
}

fn snapshot(session: &Session, current: Option<&str>, refresh_cover: bool) -> windows::core::Result<(String, Value)> {
    let app = session.SourceAppUserModelId()?.to_string();
    let media = session.TryGetMediaPropertiesAsync()?.get().ok();
    let timeline = session.GetTimelineProperties()?;
    let playback = session.GetPlaybackInfo()?;
    let ticks_ms = |d: windows::Foundation::TimeSpan| d.Duration / 10_000;
    let start = ticks_ms(timeline.StartTime()?);
    let end = ticks_ms(timeline.EndTime()?);
    let rate = playback.PlaybackRate().and_then(|r| r.Value()).ok();
    // Null when the player doesn't say.
    let shuffle = playback.IsShuffleActive().and_then(|v| v.Value()).ok();
    let repeat = playback.AutoRepeatMode().and_then(|v| v.Value()).ok().map(repeat_name);
    let state = json!({
        "app": app,
        "current": current == Some(app.as_str()),
        "title": media.as_ref().and_then(|m| m.Title().ok()).map(|s| s.to_string()),
        "artist": media.as_ref().and_then(|m| m.Artist().ok()).map(|s| s.to_string()),
        "album": media.as_ref().and_then(|m| m.AlbumTitle().ok()).map(|s| s.to_string()),
        "durationMs": end - start,
        "positionMs": ticks_ms(timeline.Position()?) - start,
        "lastUpdatedMs": ticks_to_unix_ms(timeline.LastUpdatedTime()?.UniversalTime),
        "status": status_name(playback.PlaybackStatus()?),
        "rate": rate,
        "shuffle": shuffle,
        "repeat": repeat,
        "cover": media.as_ref().and_then(|m| cover(&app, m, refresh_cover)),
    });
    Ok((app, state))
}

/// Writes [session]'s state as [event]; a poll writes only a changed state.
fn report(session: &Session, event: &str, current: Option<&str>) {
    let Ok((app, state)) = snapshot(session, current, event == "media" || event == "added") else { return };
    let mut last = LAST.get_or_init(Default::default).lock().unwrap();
    if event == "poll" && last.get(&app) == Some(&state) {
        return;
    }
    last.insert(app, state.clone());
    let mut line = json!({
        "t": now_ms(),
        "mono": START.get().unwrap().elapsed().as_secs_f64() * 1000.0,
        "event": event,
    });
    line.as_object_mut().unwrap().extend(state.as_object().unwrap().clone());
    let mut out = std::io::stdout().lock();
    let _ = writeln!(out, "{line}");
    let _ = out.flush();
}

fn current_app(manager: &Manager) -> Option<String> {
    manager.GetCurrentSession().ok()?.SourceAppUserModelId().ok().map(|s| s.to_string())
}

fn subscribe(session: &Session, manager: &Manager) -> windows::core::Result<()> {
    macro_rules! on {
        ($register:ident, $event:literal) => {{
            let m = manager.clone();
            session.$register(&TypedEventHandler::new(move |s: windows::core::Ref<Session>, _| {
                if let Some(s) = s.as_ref() {
                    report(s, $event, current_app(&m).as_deref());
                }
                Ok(())
            }))?;
        }};
    }
    on!(MediaPropertiesChanged, "media");
    on!(TimelinePropertiesChanged, "timeline");
    on!(PlaybackInfoChanged, "playback");
    Ok(())
}

/// `gsmtc ctl <app id substring> play|pause|next|previous|seek <ms>|shuffle on|off|repeat off|one|all`:
/// drives a session.
fn control(manager: &Manager, args: &[String]) -> windows::core::Result<()> {
    let [app, action, rest @ ..] = args else {
        eprintln!("usage: gsmtc ctl <app> play|pause|next|previous|seek <ms>|shuffle on|off|repeat off|one|all");
        std::process::exit(2);
    };
    let session = manager.GetSessions()?.into_iter()
        .find(|s| s.SourceAppUserModelId().map(|id| id.to_string().to_lowercase().contains(&app.to_lowercase())).unwrap_or(false));
    let Some(session) = session else {
        eprintln!("no session matching {app}");
        std::process::exit(1);
    };
    let ok = match action.as_str() {
        "play" => session.TryPlayAsync()?.get()?,
        "pause" => session.TryPauseAsync()?.get()?,
        "next" => session.TrySkipNextAsync()?.get()?,
        "previous" => session.TrySkipPreviousAsync()?.get()?,
        "seek" => {
            let ms: i64 = rest.first().and_then(|v| v.parse().ok()).unwrap_or(0);
            let start = session.GetTimelineProperties()?.StartTime()?.Duration;
            session.TryChangePlaybackPositionAsync(start + ms * 10_000)?.get()?
        }
        "shuffle" => match rest.first().map(String::as_str) {
            Some("on") => session.TryChangeShuffleActiveAsync(true)?.get()?,
            Some("off") => session.TryChangeShuffleActiveAsync(false)?.get()?,
            _ => false,
        },
        "repeat" => match rest.first().map(String::as_str) {
            Some("off") => session.TryChangeAutoRepeatModeAsync(Repeat::None)?.get()?,
            Some("one") => session.TryChangeAutoRepeatModeAsync(Repeat::Track)?.get()?,
            Some("all") => session.TryChangeAutoRepeatModeAsync(Repeat::List)?.get()?,
            _ => false,
        },
        _ => false,
    };
    println!("{}", json!({"t": now_ms(), "ctl": action, "app": session.SourceAppUserModelId()?.to_string(), "ok": ok}));
    Ok(())
}

fn main() -> windows::core::Result<()> {
    START.set(Instant::now()).unwrap();
    unsafe { RoInitialize(RO_INIT_MULTITHREADED)? };
    let manager = Manager::RequestAsync()?.get()?;
    let args: Vec<String> = std::env::args().skip(1).collect();
    if args.first().map(String::as_str) == Some("ctl") {
        return control(&manager, &args[1..]);
    }
    let changed = Mutex::new(true);
    let changed = std::sync::Arc::new(changed);
    {
        let changed = changed.clone();
        manager.SessionsChanged(&TypedEventHandler::new(move |_, _| {
            *changed.lock().unwrap() = true;
            Ok(())
        }))?;
    }
    // Keeps each session (and so its handlers) alive while Windows lists it.
    let mut subscribed: HashMap<String, Session> = HashMap::new();
    loop {
        let sessions_changed = std::mem::replace(&mut *changed.lock().unwrap(), false);
        let current = current_app(&manager);
        let sessions = manager.GetSessions()?;
        let mut seen = Vec::new();
        for session in sessions {
            let Ok(app) = session.SourceAppUserModelId().map(|s| s.to_string()) else { continue };
            seen.push(app.clone());
            if !subscribed.contains_key(&app) {
                let _ = subscribe(&session, &manager);
                subscribed.insert(app.clone(), session.clone());
                report(&session, "added", current.as_deref());
            } else if sessions_changed {
                report(&session, "sessions", current.as_deref());
            } else {
                report(&session, "poll", current.as_deref());
            }
        }
        for gone in subscribed.keys().filter(|a| !seen.contains(a)).cloned().collect::<Vec<_>>() {
            subscribed.remove(&gone);
            LAST.get_or_init(Default::default).lock().unwrap().remove(&gone);
            let mut out = std::io::stdout().lock();
            let _ = writeln!(out, "{}", json!({"t": now_ms(), "event": "removed", "app": gone}));
            let _ = out.flush();
        }
        std::thread::sleep(Duration::from_millis(250));
    }
}
