# EnterprisePulse.Java — Kullanıcı / Ekran Kılavuzu

> Bu belge tarayıcıdan veya editörden **PDF olarak yazdır** ile PDF’e dönüştürülebilir.

## 0. Giriş

- **Ürün / stack:** EnterprisePulse.Java — Spring Boot 3.5 API + Vaadin 24
- **Web:** `http://localhost:8181` · **API:** `http://localhost:8180`
- **Demo:** `admin@enterprisepulse.dev` / `Admin123!`, `manager@…` / `Manager123!`, `employee@…` / `Employee123!`
- **Önemli:** Demo veri **Pulse Teknoloji · Genel Merkez** üzerindedir. Pulse Lojistik seçilirse çoğu liste boştur.
- Liste ekranları `/r/{kaynak}` Vaadin rotalarındadır.

Bölüm numaraları sol menü gruplarına, alt başlıklar menü öğelerine karşılık gelir (ör. **2. İnsan Kaynakları** → **2.1 İzinlerim**).

---

## 1. Genel

Menüdeki **Genel** grubu. Ekranlar yetkiye göre görünür veya gizlenir.

### 1.1 Gösterge Paneli

- **Rota / kaynak:** `/`

Kurumsal gösterge paneli. Oturumdaki kullanıcının yetkilerine göre KPI kartları gösterir: onay bekleyenler, okunmamış bildirimler, aktif kullanıcılar, İK (kalan izin, bugün izinde), demirbaş zimmetleri, stok ve finans özetleri. Kartlara tıklayınca ilgili liste veya self-servis sayfasına gider. Üst barda şirket/şube seçici, dil (TR/EN), tema, bildirim zili ve profil menüsü bulunur.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 1.2 Onay Kutum

- **Rota / kaynak:** `workflow/inbox`

Onay kutusu. Kullanıcıya atanmış bekleyen onay adımlarını listeler (izin, zimmet, masraf vb.). Her satırda belge türü, talep sahibi ve özet görünür. Açınca belge özeti ile Onayla / Reddet (gerekçe) işlemleri yapılır. Süreç bitince belge durumu güncellenir ve bildirim gider.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

---

## 2. İnsan Kaynakları

Menüdeki **İnsan Kaynakları** grubu. Ekranlar yetkiye göre görünür veya gizlenir.

### 2.1 İzinlerim

- **Rota / kaynak:** `hr/my-leave`

Self-servis izin bakiyesi ve talepleri. Yıl bazında izin türü kartları (hak, kullanılan, bekleyen, kalan). Yeni talep: tür, tarih aralığı, gün hesabı, açıklama. Talepler durum etiketleriyle listelenir. Yönetici kendi bakiyesini görür; onay Onay Kutum üzerinden yürür.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 2.2 Mesaim

- **Rota / kaynak:** `hr/my-attendance`

Mesai / giriş-çıkış self-servisi. Günlük mesai, geç kalma ve fazla mesai özeti. Personel kendi kayıtlarını görür; İK puantajda düzeltme yapabilir.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 2.3 Organizasyon Şeması

- **Rota / kaynak:** `hr/org-chart`

Organizasyon şeması. Departman ve yönetici hiyerarşisine göre personeli ağaç veya iç içe listede gösterir.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 2.4 Personel

- **Rota / kaynak:** `r/employees`
- **Metadata resource:** `employees`
- **İzinler:** `employees:read`

Personel kartları listesi ve detay. Sicil, departman, pozisyon, yönetici, işe giriş, durum. Detayda izin, zimmet, ücret, eğitim, sözleşme sekmeleri (yetkiye göre).

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 2.5 Departmanlar

- **Rota / kaynak:** `r/departments`
- **Metadata resource:** `departments`
- **İzinler:** `departments:read`

Departman tanımları. Kod, ad, üst departman, yönetici. Org şeması buradaki hiyerarşiyi kullanır.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 2.6 Pozisyonlar

- **Rota / kaynak:** `r/positions`
- **Metadata resource:** `positions`
- **İzinler:** `positions:read`

Pozisyon / unvan tanımları. Departman ilişkisi ve personel atamalarında kullanılır.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 2.7 İzin Talepleri

- **Rota / kaynak:** `r/leave-requests`
- **Metadata resource:** `leave-requests`
- **İzinler:** `leave:read`

İK görünümü: tüm izin talepleri. Durum, tür, personel, tarih filtresi.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 2.8 İzin Ayarları

- **Rota / kaynak:** `r/leave-types`
- **Metadata resource:** `leave-types`
- **İzinler:** `leave:update`

İzin türü ayarları: yıllık, mazeret vb. Hak günü, taşıma, onay gereksinimi.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 2.9 Puantaj

- **Rota / kaynak:** `hr/timesheet`
- **İzinler:** `attendance:read`

Aylık puantaj çizelgesi. Personel × gün: mesai, izin, resmi tatil, İK düzeltmeleri.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 2.10 Vardiya Planlama

- **Rota / kaynak:** `r/shifts`
- **Metadata resource:** `shifts`
- **İzinler:** `attendance:read`

Vardiya tanımları ve haftalık plan. Gece vardiyası, kopyalama, personel ataması.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

---

## 3. Masraf ve Harcırah

Menüdeki **Masraf ve Harcırah** grubu. Ekranlar yetkiye göre görünür veya gizlenir.

### 3.1 Masraflarım

- **Rota / kaynak:** `expenses/my`

Personelin kendi masraf formları. Taslak, fiş eki, onaya gönderme, ödeme durumu.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 3.2 Masraf Formları

- **Rota / kaynak:** `r/expense-reports`
- **Metadata resource:** `expense-reports`
- **İzinler:** `expenses:read`

Masraf formları (yönetici/finans). Onay, red, kasa/banka ödemesi, proje bağlantısı.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 3.3 Masraf Türleri

- **Rota / kaynak:** `r/expense-categories`
- **Metadata resource:** `expense-categories`
- **İzinler:** `expenses:update`

Masraf türleri: limit, fiş zorunluluğu, harcırah ve km ücreti.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

---

## 4. Bordro

Menüdeki **Bordro** grubu. Ekranlar yetkiye göre görünür veya gizlenir.

### 4.1 Bordrolarım

- **Rota / kaynak:** `payroll/my`

Personelin kendi ücret pusulaları. Dönem, brüt, kesinti, net.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 4.2 Bordrolar

- **Rota / kaynak:** `r/payroll-periods`
- **Metadata resource:** `payroll-periods`
- **İzinler:** `payroll:read`

Bordro dönemleri. Hesaplama, onay, maaş ödemesi ve geri alma.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 4.3 Ücretler

- **Rota / kaynak:** `r/salary-records`
- **Metadata resource:** `salary-records`
- **İzinler:** `payroll:read`

Ücret kayıtları ve geçmiş.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 4.4 Bordro parametreleri

- **Rota / kaynak:** `r/payroll-parameters`
- **Metadata resource:** `payroll-parameters`
- **İzinler:** `payroll:update`

Yıllık bordro parametreleri, SGK / vergi dilimleri.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

---

## 5. İşe Alım

Menüdeki **İşe Alım** grubu. Ekranlar yetkiye göre görünür veya gizlenir.

### 5.1 Mülakatlarım

- **Rota / kaynak:** `recruitment/interviews`

Mülakatçıya atanmış mülakatlar. Puan, tavsiye, not.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 5.2 İş İlanları

- **Rota / kaynak:** `r/job-postings`
- **Metadata resource:** `job-postings`
- **İzinler:** `recruitment:read`

İş ilanları: yayınlama, bekletme, kapatma; başvuru hattına geçiş.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 5.3 Adaylar

- **Rota / kaynak:** `r/candidates`
- **Metadata resource:** `candidates`
- **İzinler:** `recruitment:read`

Aday havuzu, CV, profil.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 5.4 Başvurular

- **Rota / kaynak:** `r/job-applications`
- **Metadata resource:** `job-applications`
- **İzinler:** `recruitment:read`

Başvurular ve aşama hattı. Teklif, ret, personele dönüştürme.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

---

## 6. Performans ve Eğitim

Menüdeki **Performans ve Eğitim** grubu. Ekranlar yetkiye göre görünür veya gizlenir.

### 6.1 Değerlendirmelerim

- **Rota / kaynak:** `performance/my`

Öz ve yönetici değerlendirme formları. Hedefler, yetkinlik puanları.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 6.2 Eğitimlerim

- **Rota / kaynak:** `training/my`

Kayıtlı eğitimler, zorunlu sertifikalar, geri bildirim.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 6.3 Değerlendirme Dönemleri

- **Rota / kaynak:** `r/review-cycles`
- **Metadata resource:** `review-cycles`
- **İzinler:** `performance:read`

Performans değerlendirme dönemleri: başlatma, kapatma, özet.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 6.4 Yetkinlikler

- **Rota / kaynak:** `r/competencies`
- **Metadata resource:** `competencies`
- **İzinler:** `performance:read`

Yetkinlik kataloğu.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 6.5 Eğitim Oturumları

- **Rota / kaynak:** `r/training-sessions`
- **Metadata resource:** `training-sessions`
- **İzinler:** `training:read`

Eğitim oturumları: katılımcı, puan, maliyet.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 6.6 Eğitim Kataloğu

- **Rota / kaynak:** `r/training-courses`
- **Metadata resource:** `training-courses`
- **İzinler:** `training:read`

Eğitim kataloğu tanımları.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 6.7 Zorunlu Eğitim Uyumu

- **Rota / kaynak:** `training/compliance`
- **İzinler:** `training:read`

Zorunlu eğitim uyum matrisi (personel × kurs).

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

---

## 7. Destek

Menüdeki **Destek** grubu. Ekranlar yetkiye göre görünür veya gizlenir.

### 7.1 Destek Taleplerim

- **Rota / kaynak:** `support/my`

Self-servis destek: açma, yanıt, ek, çözüm onayı / yeniden açma, puan.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 7.2 Destek Kuyruğu

- **Rota / kaynak:** `r/tickets`
- **Metadata resource:** `tickets`
- **İzinler:** `support:read`

Destek kuyruğu: SLA, öncelik, atama, dahili not, durum geçişleri.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 7.3 Destek Ayarları

- **Rota / kaynak:** `r/ticket-categories`
- **Metadata resource:** `ticket-categories`
- **İzinler:** `support:update`

Kategori, varsayılan sorumlu, SLA hedefleri.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

---

## 8. Proje Yönetimi

Menüdeki **Proje Yönetimi** grubu. Ekranlar yetkiye göre görünür veya gizlenir.

### 8.1 Görevlerim

- **Rota / kaynak:** `projects/my-tasks`

Kullanıcıya atanmış proje görevleri.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 8.2 Zaman Çizelgesi

- **Rota / kaynak:** `projects/timesheet`

Haftalık zaman çizelgesi: projeye süre kaydı.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 8.3 Projeler

- **Rota / kaynak:** `r/projects`
- **Metadata resource:** `projects`

Proje listesi ve detay: ekip, görevler, bütçe, ilerleme.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

---

## 9. Zimmet ve Demirbaş

Menüdeki **Zimmet ve Demirbaş** grubu. Ekranlar yetkiye göre görünür veya gizlenir.

### 9.1 Zimmetlerim

- **Rota / kaynak:** `assets/my`

Zimmetimdeki demirbaşlar; iade / onay bekleyenler.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 9.2 Demirbaşlar

- **Rota / kaynak:** `r/assets`
- **Metadata resource:** `assets`
- **İzinler:** `assets:read`

Demirbaş kartları: kategori, durum (zimmetli/depo/hurda/bakım), marka-model, QR.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 9.3 Zimmetler

- **Rota / kaynak:** `r/asset-assignments`
- **Metadata resource:** `asset-assignments`
- **İzinler:** `assets:read`

Zimmet / iade / devir kayıtları ve tutanak onayları.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 9.4 Demirbaş Sayımı

- **Rota / kaynak:** `r/asset-counts`
- **Metadata resource:** `asset-counts`
- **İzinler:** `assets:read`

Demirbaş sayımı ve fark raporu.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 9.5 Demirbaş Ayarları

- **Rota / kaynak:** `r/asset-categories`
- **Metadata resource:** `asset-categories`
- **İzinler:** `assets:update`

Demirbaş kategori ve amortisman ayarları.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

---

## 10. Stok ve Depo

Menüdeki **Stok ve Depo** grubu. Ekranlar yetkiye göre görünür veya gizlenir.

### 10.1 Stok Durumu

- **Rota / kaynak:** `inventory`
- **İzinler:** `inventory:read`

Stok durumu: kalem, minimum altı, stok değeri, depo; kritik filtre.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 10.2 Ürünler

- **Rota / kaynak:** `r/products`
- **Metadata resource:** `products`
- **İzinler:** `inventory:read`

Ürün ve varyantlar: SKU, barkod, lot/seri takibi, birim.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 10.3 Stok Hareketleri

- **Rota / kaynak:** `r/stock-movements`
- **Metadata resource:** `stock-movements`
- **İzinler:** `inventory:read`

Stok fişleri: giriş, çıkış, transfer, düzeltme; lot/seri satırları.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 10.4 Stok Sayımı

- **Rota / kaynak:** `r/stock-counts`
- **Metadata resource:** `stock-counts`
- **İzinler:** `inventory:read`

Stok sayımı ve fark düzeltmesi.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 10.5 Depo ve Kategoriler

- **Rota / kaynak:** `r/warehouses`
- **Metadata resource:** `warehouses`
- **İzinler:** `inventory:update`

Depo ve stok kategori tanımları.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

---

## 11. Üretim

Menüdeki **Üretim** grubu. Ekranlar yetkiye göre görünür veya gizlenir.

### 11.1 İş emirleri

- **Rota / kaynak:** `r/work-orders`
- **Metadata resource:** `work-orders`
- **İzinler:** `production:read`

Üretim iş emirleri: serbest bırakma, üretim kaydı, malzeme, kapatma.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 11.2 Ürün ağaçları

- **Rota / kaynak:** `r/bill-of-materials`
- **Metadata resource:** `bill-of-materials`
- **İzinler:** `production:read`

Ürün ağacı (BOM): bileşenler, standart maliyet.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 11.3 Malzeme ihtiyacı

- **Rota / kaynak:** `production/requirements`
- **İzinler:** `production:read`

MRP / malzeme ihtiyacı: net ihtiyaç, talep veya iş emri.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

---

## 12. Satın Alma

Menüdeki **Satın Alma** grubu. Ekranlar yetkiye göre görünür veya gizlenir.

### 12.1 Satın Alma Talepleri

- **Rota / kaynak:** `r/purchase-requests`
- **Metadata resource:** `purchase-requests`
- **İzinler:** `workflow:start`, `purchasing:create`, `purchasing:read`

Satın alma talepleri ve onay.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 12.2 Tedarikçi Teklifleri

- **Rota / kaynak:** `r/supplier-quotes`
- **Metadata resource:** `supplier-quotes`
- **İzinler:** `purchasing:read`

Tedarikçi teklifleri ve karşılaştırma.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 12.3 Satın Alma Siparişleri

- **Rota / kaynak:** `r/purchase-orders`
- **Metadata resource:** `purchase-orders`
- **İzinler:** `purchasing:read`

Satın alma siparişleri: teslim / fatura miktarları.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 12.4 Mal Kabul

- **Rota / kaynak:** `r/goods-receipts`
- **Metadata resource:** `goods-receipts`
- **İzinler:** `purchasing:read`

Mal kabul irsaliyeleri; stoğa giriş.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 12.5 Alış Faturaları

- **Rota / kaynak:** `r/purchase-invoices`
- **Metadata resource:** `purchase-invoices`
- **İzinler:** `purchasing:read`, `finance:read`

Alış faturaları; borç ve muhasebe bağlantısı.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

---

## 13. CRM

Menüdeki **CRM** grubu. Ekranlar yetkiye göre görünür veya gizlenir.

### 13.1 Potansiyel müşteriler

- **Rota / kaynak:** `r/leads`
- **Metadata resource:** `leads`
- **İzinler:** `crm:read`

Potansiyel müşteriler; cariye / fırsata dönüştürme.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 13.2 Satış fırsatları

- **Rota / kaynak:** `r/opportunities`
- **Metadata resource:** `opportunities`
- **İzinler:** `crm:read`

Satış fırsatları: aşama, tutar, ağırlıklı değer, teklif bağlantısı.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 13.3 Aktiviteler

- **Rota / kaynak:** `r/crm-activities`
- **Metadata resource:** `crm-activities`
- **İzinler:** `crm:read`

Arama, toplantı, görev aktiviteleri; geciken / bugün.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

---

## 14. Satış

Menüdeki **Satış** grubu. Ekranlar yetkiye göre görünür veya gizlenir.

### 14.1 Satış Teklifleri

- **Rota / kaynak:** `r/sales-quotes`
- **Metadata resource:** `sales-quotes`
- **İzinler:** `sales:read`

Satış teklifleri: kalemler, kabul ile siparişe dönüşüm.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 14.2 Satış Siparişleri

- **Rota / kaynak:** `r/sales-orders`
- **Metadata resource:** `sales-orders`
- **İzinler:** `sales:read`

Satış siparişleri: rezervasyon, sevk, faturalama durumu.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 14.3 Sevk İrsaliyeleri

- **Rota / kaynak:** `r/delivery-notes`
- **Metadata resource:** `delivery-notes`
- **İzinler:** `sales:read`

Sevk irsaliyeleri; stok çıkışı.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 14.4 Satış Faturaları

- **Rota / kaynak:** `r/sales-invoices`
- **Metadata resource:** `sales-invoices`
- **İzinler:** `sales:read`, `finance:read`

Satış faturaları: tahsilat durumu, e-fatura bağlantısı.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 14.5 Satış İadeleri

- **Rota / kaynak:** `r/sales-returns`
- **Metadata resource:** `sales-returns`
- **İzinler:** `sales:read`, `finance:read`

Satış iadeleri.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

---

## 15. Cari ve Finans

Menüdeki **Cari ve Finans** grubu. Ekranlar yetkiye göre görünür veya gizlenir.

### 15.1 Cari Hesaplar

- **Rota / kaynak:** `r/business-partners`
- **Metadata resource:** `business-partners`
- **İzinler:** `partners:read`

Cari hesaplar (müşteri/tedarikçi): vade, kredi limiti, döviz, hareketler.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 15.2 Kasa ve Bankalar

- **Rota / kaynak:** `r/treasury-accounts`
- **Metadata resource:** `treasury-accounts`
- **İzinler:** `finance:read`

Kasa ve banka hesapları. Kod, tür (kasa/banka), para birimi, IBAN, aktiflik. Hareketler tahsilat/ödeme/çek ile oluşur. Demo seed Pulse Teknoloji üzerindedir.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 15.3 Tahsilatlar

- **Rota / kaynak:** `r/partner-payments`
- **Metadata resource:** `partner-payments`
- **İzinler:** `finance:read`

Tahsilat ve ödemeler: yöntem, kasa/banka, fatura mahsupları.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 15.4 Ödemeler

- **Rota / kaynak:** `r/partner-payments`
- **Metadata resource:** `partner-payments`
- **İzinler:** `finance:read`

Tahsilat ve ödemeler: yöntem, kasa/banka, fatura mahsupları.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 15.5 Çek ve Senetler

- **Rota / kaynak:** `r/cheques`
- **Metadata resource:** `cheques`
- **İzinler:** `finance:read`

Çek ve senet portföyü: yatırma, tahsil, ciro.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 15.6 Yaşlandırma

- **Rota / kaynak:** `finance/aging`
- **İzinler:** `finance:read`

Alacak / borç yaşlandırma raporu.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

---

## 16. Muhasebe

Menüdeki **Muhasebe** grubu. Ekranlar yetkiye göre görünür veya gizlenir.

### 16.1 Muhasebe paneli

- **Rota / kaynak:** `accounting`
- **İzinler:** `accounting:read`

Muhasebe paneli: kasa, alacak, borç, net kâr, aylık gelir-gider, bekleyen belgeleri muhasebeleştirme.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 16.2 Yevmiye defteri

- **Rota / kaynak:** `r/journal-entries`
- **Metadata resource:** `journal-entries`
- **İzinler:** `accounting:read`

Yevmiye defteri: fiş listeleme, kesinleştirme, ters kayıt.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 16.3 Hesap planı

- **Rota / kaynak:** `r/ledger-accounts`
- **Metadata resource:** `ledger-accounts`
- **İzinler:** `accounting:read`

Tekdüzen hesap planı; alt hesap ekleme.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 16.4 Mizan

- **Rota / kaynak:** `accounting/trial-balance`
- **İzinler:** `accounting:read`

Mizan: dönem hareketleri ve bakiyeler.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 16.5 Bilanço

- **Rota / kaynak:** `accounting/balance-sheet`
- **İzinler:** `accounting:read`

Bilanço raporu.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 16.6 Gelir tablosu

- **Rota / kaynak:** `accounting/income-statement`
- **İzinler:** `accounting:read`

Gelir tablosu.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 16.7 e-Fatura

- **Rota / kaynak:** `r/e-invoices`
- **Metadata resource:** `e-invoices`
- **İzinler:** `einvoice:read`

e-Fatura kuyruğu: UBL-TR, GİB durum simülasyonu, gelen/giden.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

---

## 17. Sözleşmeler

Menüdeki **Sözleşmeler** grubu. Ekranlar yetkiye göre görünür veya gizlenir.

### 17.1 Sözleşmeler

- **Rota / kaynak:** `r/contracts`
- **Metadata resource:** `contracts`
- **İzinler:** `contracts:read`

Sözleşmeler: cari/personel, yürürlük, yenileme, fesih, ekler.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 17.2 Sözleşme Türleri

- **Rota / kaynak:** `r/contract-types`
- **Metadata resource:** `contract-types`
- **İzinler:** `contracts:update`

Sözleşme türü tanımları.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

---

## 18. Bütçe

Menüdeki **Bütçe** grubu. Ekranlar yetkiye göre görünür veya gizlenir.

### 18.1 Bütçeler

- **Rota / kaynak:** `r/budgets`
- **Metadata resource:** `budgets`
- **İzinler:** `budgets:read`

Şirket / departman bütçeleri: plan vs gerçekleşen, sapma.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 18.2 Bütçe Girişleri

- **Rota / kaynak:** `r/budget-entries`
- **Metadata resource:** `budget-entries`
- **İzinler:** `budgets:read`

Gerçekleşen bütçe girişleri (kira, reklam vb.).

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 18.3 Bütçe Kalemleri

- **Rota / kaynak:** `r/budget-items`
- **Metadata resource:** `budget-items`
- **İzinler:** `budgets:update`

Bütçe kalemi ve kategori eşlemesi.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

---

## 19. Araç ve Filo

Menüdeki **Araç ve Filo** grubu. Ekranlar yetkiye göre görünür veya gizlenir.

### 19.1 Araçlar

- **Rota / kaynak:** `r/vehicles`
- **Metadata resource:** `vehicles`
- **İzinler:** `fleet:read`

Filo araç kartları: sürücü, bakım, muayene, sigorta.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 19.2 Yakıt Defteri

- **Rota / kaynak:** `r/vehicle-fuel-entries`
- **Metadata resource:** `vehicle-fuel-entries`
- **İzinler:** `fleet:read`

Yakıt defteri.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 19.3 Araçlarım

- **Rota / kaynak:** `fleet/my`

Sürücünün zimmetli araçları; yakıt ve km girişi.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

---

## 20. Raporlar

Menüdeki **Raporlar** grubu. Ekranlar yetkiye göre görünür veya gizlenir.

### 20.1 Satış Analizi

- **Rota / kaynak:** `reports/sales`
- **İzinler:** `sales:read`

Satış analizi raporu.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 20.2 Alış Analizi

- **Rota / kaynak:** `reports/purchases`
- **İzinler:** `purchasing:read`

Alış analizi.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 20.3 Nakit Akışı

- **Rota / kaynak:** `reports/cash-flow`
- **İzinler:** `finance:read`

Nakit akışı.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

---

## 21. Onay Süreçleri

Menüdeki **Onay Süreçleri** grubu. Ekranlar yetkiye göre görünür veya gizlenir.

### 21.1 Tüm Talepler

- **Rota / kaynak:** `r/workflow-instances`
- **Metadata resource:** `workflow-instances`
- **İzinler:** `workflow:read`

Tüm onay süreç örnekleri (yönetim).

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 21.2 Akış Tanımları

- **Rota / kaynak:** `r/workflow-definitions`
- **Metadata resource:** `workflow-definitions`
- **İzinler:** `workflow:read`

Onay akış tanımları: adımlar, roller.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

---

## 22. Organizasyon

Menüdeki **Organizasyon** grubu. Ekranlar yetkiye göre görünür veya gizlenir.

### 22.1 Şirketler

- **Rota / kaynak:** `r/companies`
- **Metadata resource:** `companies`
- **İzinler:** `companies:read`

Şirket (kiracı) kartları.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 22.2 Şubeler

- **Rota / kaynak:** `r/branches`
- **Metadata resource:** `branches`
- **İzinler:** `branches:read`

Şube tanımları.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 22.3 Kullanıcılar

- **Rota / kaynak:** `r/users`
- **Metadata resource:** `users`
- **İzinler:** `users:read`

Kullanıcı hesapları ve şirket üyelikleri.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 22.4 Roller ve İzinler

- **Rota / kaynak:** `roles`
- **İzinler:** `roles:read`

Rol ve izin matrisi.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

---

## 23. Sistem

Menüdeki **Sistem** grubu. Ekranlar yetkiye göre görünür veya gizlenir.

### 23.1 Denetim Kaydı

- **Rota / kaynak:** `r/audit-logs`
- **Metadata resource:** `audit-logs`
- **İzinler:** `audit:read`

Denetim kaydı: kim, ne zaman, hangi alan değişti (okunabilir Geçmiş).

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

### 23.2 Ayarlar

- **Rota / kaynak:** `settings`
- **İzinler:** `settings:read`

Şirket ayarları: para birimi, kur, KDV, birim, numaralandırma, genel.

**Nasıl kullanılır:**
1. Menüden ilgili öğeyi açın.
2. Liste ise Ara / filtre ile kaydı bulun; **Yeni** veya satır detayı.
3. Formu kaydedin; onaylı belgeler **Onay Kutum**’a düşer.
4. Detayda **Geçmiş**, **Dosyalar** ve durum aksiyonlarını kullanın.

---

## Ek A. Ortak kalıplar

- **Liste:** arama, rozetler, sayfalama, + Yeni
- **Form/detay:** doğrulama, ilişkili seçiciler, durum butonları
- **Onay:** çok adımlı workflow, red gerekçesi, bildirim
- **Çok şirket:** üst bar şirket/şube seçici → `companyId` bağlamı

## Ek B. İlgili belgeler

- Teknik kılavuz: [`teknik-kilavuz.md`](teknik-kilavuz.md)
- Kısa başlangıç: kök `README.md`
