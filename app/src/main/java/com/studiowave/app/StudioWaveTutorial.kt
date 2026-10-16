package com.studiowave.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class TutorialLesson(
    val title: String,
    val summary: String,
    val steps: List<String>
)

@Composable
internal fun StudioWaveTutorialWorkspace(onBack: () -> Unit) {
    val p = LocalStudioWavePalette.current
    val lessons = remember {
        listOf(
            TutorialLesson("01 · Mulai proyek", "Buat set baru dan atur tempo sebelum menyusun musik.", listOf(
                "Buka Settings → Project → New Empty Project, lalu beri nama proyek.",
                "Atur BPM dari transport bar. Mulai dari 90–120 BPM untuk latihan umum.",
                "Simpan pekerjaan secara berkala melalui Save / Save As dan pertahankan file .sdaw."
            )),
            TutorialLesson("02 · Tambahkan audio ke Arrangement", "Impor loop, vokal, rekaman, atau one-shot ke track audio.", listOf(
                "Buka Browser dan pilih Import Audio, atau gunakan tombol impor pada Audio Editor.",
                "Pilih file audio yang tersedia di perangkat. Tunggu status impor selesai dan periksa nama sumber pada clip.",
                "Tekan Play untuk mendengar dari posisi playhead. Jika clip bertanda MISSING, pilih ulang sumber file yang benar.",
                "Geser clip untuk mengatur posisi; gunakan zoom dan snap untuk menyelaraskan ketukan."
            )),
            TutorialLesson("03 · Buat melodi dengan Piano Roll", "Gunakan track MIDI untuk membuat nada, chord, dan bassline.", listOf(
                "Pilih atau buat track MIDI, lalu buka Piano Roll dari clip MIDI.",
                "Ketuk area grid untuk membuat note; seret note untuk mengubah posisi dan gunakan tepi note untuk mengubah durasi.",
                "Sesuaikan snap dan zoom agar note tepat pada beat. Gunakan kontrol velocity jika tersedia.",
                "Pilih instrumen pada Device View untuk mendengar note. Jika instrumen belum tersedia, periksa device dan log audio."
            )),
            TutorialLesson("04 · Susun beat drum", "Bangun pola kick, snare, hi-hat, dan perkusi.", listOf(
                "Tambahkan atau pilih track Drum, lalu buka Drum Sequencer.",
                "Aktifkan langkah pada grid untuk membuat pola; mulai dengan kick pada beat utama dan snare pada backbeat.",
                "Atur level tiap lane/pad agar kick dan snare seimbang.",
                "Kembali ke Arrangement untuk menempatkan pattern sepanjang lagu."
            )),
            TutorialLesson("05 · Atur track dan pattern", "Rapikan instrumen, pattern, dan audio agar proyek mudah dibaca.", listOf(
                "Beri nama track sesuai peran: Drums, Bass, Chords, Lead, Vocals, FX.",
                "Gunakan mute dan solo untuk mengisolasi bagian ketika mengedit.",
                "Jaga satu fungsi utama per track saat masih belajar; gunakan nama yang konsisten.",
                "Gunakan duplicate/copy bila tersedia, lalu periksa posisi clip agar tidak bertumpuk tanpa sengaja."
            )),
            TutorialLesson("06 · Mixing dasar", "Atur level dan panorama sebelum menambahkan efek.", listOf(
                "Buka Mixer dan mulai dengan fader track rendah; naikkan perlahan sambil mendengarkan.",
                "Jadikan kick/bass sebagai acuan level, lalu tempatkan elemen lain di bawahnya.",
                "Gunakan pan untuk memberi ruang kiri/kanan; pertahankan kick dan bass dekat tengah.",
                "Perhatikan peak meter master. Hindari clipping dan turunkan track bila master terlalu keras."
            )),
            TutorialLesson("07 · Device dan efek", "Gunakan efek yang tersedia dan dengarkan hasilnya.", listOf(
                "Pilih track lalu buka Device View untuk melihat device chain.",
                "Aktifkan hanya efek yang diperlukan dan dengarkan perubahan satu per satu.",
                "Gunakan EQ untuk mengurangi frekuensi yang mengganggu; reverb/delay secukupnya agar mix tidak keruh.",
                "Jika parameter tidak mengubah suara, catat masalahnya melalui Settings → Diagnostics & Logs."
            )),
            TutorialLesson("08 · Simpan dan pulihkan proyek", "Jaga agar track, clip, MIDI, tempo, dan parameter tetap tersimpan.", listOf(
                "Gunakan Save As untuk mengekspor proyek .sdaw ke lokasi yang mudah ditemukan.",
                "Buka kembali file .sdaw untuk memastikan proyek dapat dipulihkan.",
                "Simpan sumber audio asli di lokasi stabil; memindahkan atau menghapus file dapat membuat clip menjadi MISSING.",
                "Sebelum mengirim laporan bug, buka Diagnostics & Logs dan gunakan Export / Share Log."
            )),
            TutorialLesson("09 · Atasi audio lambat atau tidak terdengar", "Langkah pemeriksaan cepat ketika playback bermasalah.", listOf(
                "Pastikan clip tidak berstatus MISSING dan format audio dapat dibaca Android.",
                "Coba Settings → Audio: AUTO untuk pilihan otomatis, LOW LATENCY untuk respons cepat, BALANCED untuk umum, atau STABLE jika terdengar putus-putus.",
                "Tutup aplikasi audio lain bila perangkat berbagi output, lalu stop dan mulai playback lagi.",
                "Buka Settings → Diagnostics & Logs. Periksa underrun, output status, render time, dan error decoding.",
                "Profil hanya mengubah konfigurasi yang didukung engine; profil tidak memperbaiki file rusak atau fitur instrumen yang belum tersedia."
            ))
        )
    }
    var expandedLesson by remember { mutableIntStateOf(0) }

    Column(
        Modifier.fillMaxSize().background(p.canvas, RoundedCornerShape(2.dp))
            .border(1.dp, p.border, RoundedCornerShape(2.dp)).padding(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("STUDIOWAVE · MUSIC GUIDE", color = p.accent, fontSize = 10.sp, fontWeight = FontWeight.Black)
                Text("Tutorial membuat musik", color = p.text, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text("Panduan sentuh dari proyek kosong sampai mixing dasar.", color = p.muted, fontSize = 9.sp)
            }
            Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = p.control, contentColor = p.text), shape = RoundedCornerShape(1.dp)) {
                Text("ARRANGEMENT", fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(7.dp))
        HorizontalDivider(color = p.border)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Surface(color = p.panel, shape = RoundedCornerShape(1.dp), border = androidx.compose.foundation.BorderStroke(1.dp, p.accent.copy(alpha = 0.55f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("ALUR CEPAT", color = p.accent, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    Text("Buat proyek → impor audio atau buat MIDI/drum → susun clip di Arrangement → atur Mixer → tambahkan efek → simpan .sdaw.", color = p.text, fontSize = 10.sp)
                    Text("Ketuk setiap bab untuk membuka langkah-langkahnya.", color = p.muted, fontSize = 8.sp)
                }
            }
            lessons.forEachIndexed { index, lesson ->
                val isExpanded = expandedLesson == index
                Surface(
                    color = if (isExpanded) p.raised else p.panel,
                    shape = RoundedCornerShape(1.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isExpanded) p.accent.copy(alpha = 0.65f) else p.border),
                    modifier = Modifier.fillMaxWidth().clickable { expandedLesson = if (isExpanded) -1 else index }
                ) {
                    Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(lesson.title, color = if (isExpanded) p.accent else p.text, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(if (isExpanded) "−" else "+", color = p.accent, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                        Text(lesson.summary, color = p.muted, fontSize = 8.sp)
                        if (isExpanded) {
                            HorizontalDivider(color = p.border)
                            lesson.steps.forEachIndexed { stepIndex, step ->
                                Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Top) {
                                    Text("${stepIndex + 1}.", color = p.accent, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                    Text(step, color = p.text, fontSize = 9.sp, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
