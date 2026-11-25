package se.pundaren.guide.app.ui

import android.Manifest
import android.app.KeyguardManager
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import se.pundaren.guide.app.AppGraph
import se.pundaren.guide.app.ConvoyPhase
import se.pundaren.guide.app.ConvoyView
import se.pundaren.guide.convoy.ConvoyInvite
import se.pundaren.guide.convoy.Freshness
import se.pundaren.guide.convoy.TransportState
import se.pundaren.guide.truth.api.GeoPoint
import se.pundaren.guide.truth.core.geo.Geo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH.mm")
private fun clock(at: Instant): String = CLOCK.format(at.atZone(ZoneId.systemDefault()))

/** Convoy för två (07): starta en delning och visa QR-koden, eller skanna den andras kod. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConvoyDialog(graph: AppGraph, own: GeoPoint?, onShowPeer: (GeoPoint) -> Unit, onDismiss: () -> Unit) {
    val view by graph.convoy.view.collectAsState()
    val settings by graph.settings.value.collectAsState()
    val context = LocalContext.current
    var scanning by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }
    var hours by remember { mutableLongStateOf(6L) }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> scanning = granted }
    // Notisen under delningen kräver användarens ja på nyare Android. Delningen börjar oavsett svaret.
    var afterNotice by remember { mutableStateOf<(() -> Unit)?>(null) }
    val askNotice = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { afterNotice?.invoke(); afterNotice = null }
    fun withNotice(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            afterNotice = action
            askNotice.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else action()
    }
    // Skärmlås krävs (07, T-08): utan det kan den som hittar telefonen se var den andra är.
    val locked = remember(view.phase) { context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true }

    Panel(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Convoy", color = GuideColors.OnSurface, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            view.message?.let { Text(it, color = GuideColors.Warm, fontSize = 16.sp) }

            when (view.phase) {
                ConvoyPhase.CONNECTING -> Text("Ansluter till reläet …", color = GuideColors.OnSurface, fontSize = 16.sp)

                ConvoyPhase.SHARING -> Sharing(view, own, onShowPeer = { onShowPeer(it); onDismiss() }, onShare = { text ->
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Dela inbjudan"))
                }, onStop = { graph.convoy.requestStop() })

                ConvoyPhase.IDLE -> if (scanning) {
                    Text("Rikta kameran mot den andras kod.", color = GuideColors.OnSurface, fontSize = 16.sp)
                    QrScanner(onInvite = { text ->
                        scanning = false
                        withNotice { graph.convoy.join(text) }
                    })
                    PillButton("Avbryt") { scanning = false }
                } else {
                    Text(
                        "Två telefoner delar position med varandra under en tur. Positionen krypteras här på telefonen, och reläet kan inte läsa den.",
                        color = GuideColors.OnSurface, fontSize = 16.sp,
                    )
                    OutlinedTextField(
                        value = settings.convoyRelayUrl,
                        onValueChange = { url -> graph.settings.update { it.copy(convoyRelayUrl = url.trim()) } },
                        label = { Text("Reläets adress (https://…)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(2L, 6L, 12L, 24L).forEach { h -> PillButton("$h tim", primary = hours == h) { hours = h } }
                    }
                    Text(
                        "När du startar delar du din position med den som skannar koden, till kl. ${clock(Instant.now().plusSeconds(hours * 3600))} eller tills du trycker Sluta dela.",
                        color = GuideColors.OnSurfaceVariant, fontSize = 14.sp,
                    )
                    if (!locked) Text(
                        "Convoy kräver skärmlås. Utan det kan den som hittar telefonen se var den andra är. Sätt ett skärmlås i telefonens inställningar och öppna rutan igen.",
                        color = GuideColors.Warm, fontSize = 16.sp,
                    )
                    PillButton("Starta och visa kod", primary = true, enabled = locked && settings.convoyRelayUrl.startsWith("https://")) {
                        withNotice { graph.convoy.start(settings.convoyRelayUrl, hours) }
                    }

                    Text("Gå med i någon annans tur", color = GuideColors.OnSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "När du går med delar du din position med den som bjöd in, till turens sluttid eller tills du trycker Sluta dela.",
                        color = GuideColors.OnSurfaceVariant, fontSize = 14.sp,
                    )
                    PillButton("Skanna kod", primary = true, enabled = locked) {
                        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scanning = true
                        else askCamera.launch(Manifest.permission.CAMERA)
                    }
                    OutlinedTextField(
                        value = pasted, onValueChange = { pasted = it.trim() },
                        label = { Text("eller klistra in inbjudan") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    PillButton("Gå med", enabled = locked && pasted.startsWith(ConvoyInvite.PREFIX)) {
                        val invite = pasted
                        pasted = ""
                        withNotice { graph.convoy.join(invite) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Sharing(view: ConvoyView, own: GeoPoint?, onShowPeer: (GeoPoint) -> Unit, onShare: (String) -> Unit, onStop: () -> Unit) {
    Text(
        "Du delar din position" + (view.expiresAtUtc?.let { " till kl. ${clock(it)}." } ?: "."),
        color = GuideColors.OnSurface, fontSize = 18.sp,
    )
    Text("En notis står kvar så länge delningen pågår. Delningen fortsätter när appen ligger i bakgrunden.", color = GuideColors.OnSurfaceVariant, fontSize = 14.sp)
    if (view.notSending) {
        Text("Din position sänds inte just nu: det finns ingen verklig position med känd noggrannhet.", color = GuideColors.Warm, fontSize = 16.sp)
    }
    Text(
        when (view.transport) {
            TransportState.CONNECTED -> "Kontakt med reläet."
            TransportState.DEGRADED -> "Kontakten med reläet hackar."
            TransportState.OFFLINE -> "Ingen kontakt med reläet. Inga positioner kommer fram."
        },
        color = if (view.transport == TransportState.CONNECTED) GuideColors.OnSurfaceVariant else GuideColors.Warm, fontSize = 16.sp,
    )

    view.peerEmergencyAtUtc?.let {
        Text("NÖDSIGNAL från den andra kl. ${clock(it)}.", color = GuideColors.Error, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
    if (view.peerStopped) Text("Den andra har slutat dela.", color = GuideColors.Warm, fontSize = 16.sp)

    val peer = view.peer
    if (peer != null) {
        val where = own?.let { "${Geo.formatDistance(Geo.distanceMeters(it, peer.point))} åt ${Geo.compassSv(Geo.bearingDegrees(it, peer.point))}, fågelvägen" }
            ?: "avståndet går inte att räkna utan egen position"
        Text(
            "Den andra: $where · ${peer.presentation.label} · ±${peer.accuracyMeters.toInt()} m" +
                (if (peer.presentation.freshness == Freshness.STALE) ". För gammal för att visas på kartan." else ""),
            color = GuideColors.OnSurface, fontSize = 18.sp,
        )
    } else if (view.inviteText == null && !view.peerStopped) {
        Text("Väntar på den andras första position.", color = GuideColors.OnSurfaceVariant, fontSize = 16.sp)
    }

    view.verificationCode?.let {
        Text("Kontrollkod: $it", color = GuideColors.Truth, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Läs koden högt för varandra. Om den inte är densamma har någon annan löst in inbjudan: sluta dela.",
            color = GuideColors.OnSurfaceVariant, fontSize = 14.sp,
        )
    }

    view.inviteText?.let { text ->
        val qr = remember(text) { qrBitmap(text).asImageBitmap() }
        Image(qr, contentDescription = "Inbjudan som QR-kod", modifier = Modifier.size(280.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(4.dp))
        Text(
            "Låt den andra skanna koden. Den gäller i tio minuter och kan användas en gång. Koden innehåller nyckeln: visa den bara för den som ska med.",
            color = GuideColors.OnSurfaceVariant, fontSize = 14.sp,
        )
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (peer != null && peer.presentation.showOnMap) PillButton("Visa på kartan") { onShowPeer(peer.point) }
        view.inviteText?.let { text -> PillButton("Dela som text") { onShare(text) } }
        PillButton("Sluta dela", primary = true, onClick = onStop)
    }
}

private fun qrBitmap(text: String, size: Int = 720): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(size * size) { i -> if (matrix.get(i % size, i / size)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

/** Kamerabilden med QR-läsning. Kameran är bara igång medan den här rutan visas. */
@Composable
private fun QrScanner(onInvite: (String) -> Unit) {
    val context = LocalContext.current
    val owner = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<LifecycleOwner>().firstOrNull()
    }
    if (owner == null) {
        Text("Kameran går inte att starta här. Klistra in inbjudan i stället.", color = GuideColors.Warm, fontSize = 16.sp)
        return
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val main = remember { Handler(Looper.getMainLooper()) }
    val done = remember { AtomicBoolean(false) }
    val provider = remember { arrayOfNulls<ProcessCameraProvider>(1) }
    var failed by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            done.set(true)
            provider[0]?.unbindAll()
            executor.shutdown()
        }
    }
    if (failed) Text("Kameran gick inte att starta. Klistra in inbjudan i stället.", color = GuideColors.Warm, fontSize = 16.sp)

    AndroidView(
        modifier = Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(16.dp)),
        factory = { ctx ->
            PreviewView(ctx).also { preview ->
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    try {
                        val cameras = future.get()
                        provider[0] = cameras
                        val reader = QRCodeReader()
                        val shown = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
                        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                        analysis.setAnalyzer(executor) { image ->
                            val text = try { decode(image, reader) } finally { image.close() }
                            if (text != null && text.startsWith(ConvoyInvite.PREFIX) && done.compareAndSet(false, true)) main.post { onInvite(text) }
                        }
                        cameras.unbindAll()
                        if (!done.get()) cameras.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, shown, analysis)
                    } catch (e: Exception) {
                        failed = true
                    }
                }, { main.post(it) })
            }
        },
    )
}

/** Läser en QR-kod ur kamerabildens ljushetsplan. Null om bilden inte innehåller någon läsbar kod. */
private fun decode(image: ImageProxy, reader: QRCodeReader): String? {
    val plane = image.planes[0]
    val bytes = ByteArray(plane.buffer.remaining()).also { plane.buffer.get(it) }
    return try {
        val source = PlanarYUVLuminanceSource(bytes, plane.rowStride, image.height, 0, 0, image.width, image.height, false)
        reader.decode(BinaryBitmap(HybridBinarizer(source))).text
    } catch (e: Exception) {
        null
    } finally {
        reader.reset()
    }
}
