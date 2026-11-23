import SwiftUI
import UIKit
import Shared

@main
struct MesCoursesApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all)
                // Lien mescourses://import?url=… (extension « Partager ») : import de la recette.
                .onOpenURL { url in IosApp.shared.handleIncoming(text: url.absoluteString) }
                .onReceive(NotificationCenter.default.publisher(for: UIApplication.didReceiveMemoryWarningNotification)) { _ in
                    IosApp.shared.onMemoryWarning()
                }
        }
    }
}

/// L'interface de l'app (Compose Multiplatform, code commun avec Android).
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(llm: LiteRtLlm(), embedder: LiteRtEmbedder())
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
