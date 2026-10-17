use std::sync::Once;

static INIT: Once = Once::new();

pub(crate) fn init() {
    INIT.call_once(|| {
        let _ = android_logger::init_once(
            android_logger::Config::default()
                .with_tag("EasyTier")
                .with_max_level(log::LevelFilter::Info),
        );
    });
}
