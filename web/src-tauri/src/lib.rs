use rand::distr::{Alphanumeric, SampleString};
use tauri::{Emitter, Manager};

#[tauri::command]
fn desktop_vault_password() -> Result<String, String> {
    let entry = keyring::Entry::new("sg.innercosmos.desktop", "stronghold-v1")
        .map_err(|_| "unable to open the operating-system credential vault".to_string())?;
    match entry.get_password() {
        Ok(password) if password.len() >= 32 => Ok(password),
        _ => {
            let password = Alphanumeric.sample_string(&mut rand::rng(), 64);
            entry.set_password(&password)
                .map_err(|_| "unable to persist the Stronghold unlock secret".to_string())?;
            Ok(password)
        }
    }
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let mut builder = tauri::Builder::default();

    #[cfg(desktop)]
    {
        builder = builder.plugin(tauri_plugin_single_instance::init(|app, args, _cwd| {
            if let Some(window) = app.get_webview_window("main") {
                let _ = window.show();
                let _ = window.set_focus();
            }
            let owned_links: Vec<String> = args.into_iter()
                .filter(|value| value.starts_with("innercosmos://"))
                .collect();
            if !owned_links.is_empty() {
                let _ = app.emit("inner-cosmos-deep-link", owned_links);
            }
        }));
    }

    builder
        .plugin(tauri_plugin_deep_link::init())
        .plugin(tauri_plugin_notification::init())
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(tauri::generate_handler![desktop_vault_password])
        .setup(|app| {
            let salt_path = app.path().app_local_data_dir()
                .map_err(|error| format!("could not resolve app local data path: {error}"))?
                .join("stronghold-salt.txt");
            app.handle().plugin(tauri_plugin_stronghold::Builder::with_argon2(&salt_path).build())?;
            #[cfg(any(windows, target_os = "linux"))]
            {
                use tauri_plugin_deep_link::DeepLinkExt;
                if cfg!(debug_assertions) { app.deep_link().register_all()?; }
            }
            if cfg!(debug_assertions) {
                app.handle().plugin(tauri_plugin_log::Builder::default()
                    .level(log::LevelFilter::Warn).build())?;
            }
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running Inner Cosmos");
}
