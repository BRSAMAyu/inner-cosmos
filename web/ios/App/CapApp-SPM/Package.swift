// swift-tools-version: 5.9
import PackageDescription

// DO NOT MODIFY THIS FILE - managed by Capacitor CLI commands
let package = Package(
    name: "CapApp-SPM",
    platforms: [.iOS(.v15)],
    products: [
        .library(
            name: "CapApp-SPM",
            targets: ["CapApp-SPM"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", exact: "8.4.2"),
        .package(name: "AparajitaCapacitorSecureStorage", path: "..\..\..\node_modules\.pnpm\@aparajita+capacitor-secure-storage@8.0.0\node_modules\@aparajita\capacitor-secure-storage"),
        .package(name: "CapacitorApp", path: "..\..\..\node_modules\.pnpm\@capacitor+app@8.1.1_@capacitor+core@8.4.2\node_modules\@capacitor\app"),
        .package(name: "CapacitorBrowser", path: "..\..\..\node_modules\.pnpm\@capacitor+browser@8.0.1_@capacitor+core@8.4.2\node_modules\@capacitor\browser"),
        .package(name: "CapacitorDevice", path: "..\..\..\node_modules\.pnpm\@capacitor+device@8.0.3_@capacitor+core@8.4.2\node_modules\@capacitor\device"),
        .package(name: "CapacitorHaptics", path: "..\..\..\node_modules\.pnpm\@capacitor+haptics@8.0.2_@capacitor+core@8.4.2\node_modules\@capacitor\haptics"),
        .package(name: "CapacitorKeyboard", path: "..\..\..\node_modules\.pnpm\@capacitor+keyboard@8.0.5_@capacitor+core@8.4.2\node_modules\@capacitor\keyboard"),
        .package(name: "CapacitorLocalNotifications", path: "..\..\..\node_modules\.pnpm\@capacitor+local-notifications@8.2.1_@capacitor+core@8.4.2\node_modules\@capacitor\local-notifications"),
        .package(name: "CapacitorNetwork", path: "..\..\..\node_modules\.pnpm\@capacitor+network@8.0.1_@capacitor+core@8.4.2\node_modules\@capacitor\network"),
        .package(name: "CapacitorPushNotifications", path: "..\..\..\node_modules\.pnpm\@capacitor+push-notifications@8.1.2_@capacitor+core@8.4.2\node_modules\@capacitor\push-notifications"),
        .package(name: "CapacitorSplashScreen", path: "..\..\..\node_modules\.pnpm\@capacitor+splash-screen@8.0.2_@capacitor+core@8.4.2\node_modules\@capacitor\splash-screen"),
        .package(name: "CapacitorStatusBar", path: "..\..\..\node_modules\.pnpm\@capacitor+status-bar@8.0.3_@capacitor+core@8.4.2\node_modules\@capacitor\status-bar")
    ],
    targets: [
        .target(
            name: "CapApp-SPM",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm"),
                .product(name: "AparajitaCapacitorSecureStorage", package: "AparajitaCapacitorSecureStorage"),
                .product(name: "CapacitorApp", package: "CapacitorApp"),
                .product(name: "CapacitorBrowser", package: "CapacitorBrowser"),
                .product(name: "CapacitorDevice", package: "CapacitorDevice"),
                .product(name: "CapacitorHaptics", package: "CapacitorHaptics"),
                .product(name: "CapacitorKeyboard", package: "CapacitorKeyboard"),
                .product(name: "CapacitorLocalNotifications", package: "CapacitorLocalNotifications"),
                .product(name: "CapacitorNetwork", package: "CapacitorNetwork"),
                .product(name: "CapacitorPushNotifications", package: "CapacitorPushNotifications"),
                .product(name: "CapacitorSplashScreen", package: "CapacitorSplashScreen"),
                .product(name: "CapacitorStatusBar", package: "CapacitorStatusBar")
            ]
        )
    ]
)
