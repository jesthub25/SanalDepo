# SanalDepo

PlayerVaults mantığında, sanal sandık / depo eklentisi. Geliştirici: **Jestron**

- **Sürüm uyumu:** Spigot / Paper / Purpur **1.16.5 – 1.21.x** (Java 16+)
- **Veritabanı:** SQLite (varsayılan) veya MySQL / MariaDB
- **İndir:** [Releases](https://github.com/jesthub25/SanalDepo/releases) → `SanalDepo-x.y.z.jar` → `plugins/` klasörüne at

## Komutlar

| Komut | Açıklama | Yetki |
|---|---|---|
| `/depo` | Depo menüsünü açar | `sanaldepo.kullan` |
| `/depo <numara>` | Depoyu direkt açar | `sanaldepo.kullan` |
| `/depo <oyuncu>` | Oyuncunun depo menüsü (çevrimdışı da olur) | `sanaldepo.admin.gor` |
| `/depo <oyuncu> <numara>` | Oyuncunun deposunu açar | `sanaldepo.admin.gor` |
| `/depo yenile` | config.yml ve mesajlar.yml yeniden yüklenir | `sanaldepo.admin.yenile` |
| `/depo yardim` | Yardım | – |

Takma ad: `/sanaldepo`

## Yetkiler

| Yetki | Varsayılan | Açıklama |
|---|---|---|
| `sanaldepo.kullan` | herkes | Kendi depolarını kullanma |
| `sanaldepo.depo.<sayı>` | – | O sayı kadar depo hakkı (en yükseği geçerli), ör. `sanaldepo.depo.5` |
| `sanaldepo.admin.gor` | op | Başkasının depolarını görme (salt okunur) |
| `sanaldepo.admin.duzenle` | op | Başkasının deposuna eşya koyma / alma |
| `sanaldepo.admin.yenile` | op | `/depo yenile` |
| `sanaldepo.bypass.creative` | op | Yaratıcı modda kullanabilme |
| `sanaldepo.bypass.dunya` | op | Engelli dünyalarda kullanabilme |
| `sanaldepo.bypass.bekleme` | op | Açma bekleme süresine takılmama |
| `sanaldepo.bypass.yasakli` | op | Yasaklı eşyaları koyabilme |
| `sanaldepo.admin` | op | Yukarıdaki tüm yönetici yetkileri |

### Depo hakkı nasıl hesaplanır?
1. `sanaldepo.depo.28` … `sanaldepo.depo.1` arasında oyuncunun sahip olduğu **en yüksek** sayı alınır.
2. Hiçbiri yoksa `depo.varsayilan-hak` kullanılır.
3. Sonuç **asla** `depo.maksimum-hak` değerini geçemez — **OP olsa bile**. Bu değer en fazla 28'dir (54'lük menünün kenarlar hariç iç slot sayısı).
4. `op-maksimum-alir: false` yapılırsa OP'ler de sadece kendilerine açıkça verilen `sanaldepo.depo.<sayı>` kadar depo kullanır.

LuckPerms örneği:
```
/lp group vip permission set sanaldepo.depo.3
/lp group mvp permission set sanaldepo.depo.6
```

## Menü

- 54 slot: dama desenli cam kenarlar, farklı renkli köşeler
- Ortada (7x4 = 28 slot) `1, 2, 3 …` diye sıralı sandık simgeleri; simgenin adedi depo numarasını gösterir
- Hakkın olan depolar sandık, olmayanlar kilitli simge (veya `goster: false` ile hiç görünmez)
- Her sandıkta doluluk: `12/54 (22%)` ve renkli doluluk çubuğu
- Alt ortada oyuncu kafası ile bilgi (hak, toplam dolu slot); yönetici görünümünde UUID, çevrimiçi durumu, kayıtlı depo sayısı
- Menüden açılan depo kapatılınca menüye geri dönülür (`kapaninca-menuye-don`)

Tüm başlıklar, malzemeler, isimler, lore satırları ve sesler `config.yml`'den değiştirilebilir. HEX renk desteklenir: `&#FFAA00`.

**Yer tutucular** — menü depo simgesi: `%no% %dolu% %bos% %boyut% %yuzde% %bar% %oyuncu%` · bilgi simgesi: `%oyuncu% %uuid% %durum% %hak% %maks% %kayitli% %toplam%`

## Veritabanı ve performans

- Oyuncu girişte depoları **tek sorguda** arka planda yüklenir; `/depo` anında açılır.
- Tüm veritabanı işlemleri **tek IO iş parçacığında sırayla** yapılır: ana iş parçacığı (TPS) hiç beklemez ve "çıkışta kaydet → hızlı tekrar girişte yükle" sırası her zaman doğrudur.
- Depo sadece **içeriği değiştiyse** yazılır; boş depolar için satır tutulmaz.
- Veri GZIP ile sıkıştırılır. Paper'da Paper'ın `serializeAsBytes` formatı (sürüm yükseltmelerinde en güvenlisi), Spigot'ta Bukkit formatı kullanılır. İki format da her zaman okunur.
- SQLite: WAL modu, tek bağlantı. MySQL: HikariCP havuzu, prepared statement önbelleği.
- Çevrimdışı oyuncular bellekte tutulmaz; çıkışta kayıt biter bitmez bellekten atılır.

Tablolar (`tablo-oneki` = `sanaldepo_`):
```
sanaldepo_vaults  (uuid, vault, data, updated)   PRIMARY KEY (uuid, vault)
sanaldepo_players (uuid, name, name_lower, last_seen)  -- çevrimdışı oyuncu adı araması
```

### BungeeCord / Velocity (aynı MySQL, birden fazla sunucu)
`veritabani.tur: MYSQL` ve `yukleme-gecikmesi-tick: 40` yapın. Böylece oyuncu sunucu değiştirdiğinde önceki sunucunun kaydı bitmeden yeni sunucu yükleme yapmaz.

## Güvenlik / dupe koruması

- Aynı depo için **tek bir canlı envanter** vardır. Sahibi ve yöneticiler aynı anda açsa bile aynı envanteri görür; kopya oluşamaz.
- Envanter, son izleyici kapatana kadar asıl kaynaktır; kapanınca içerik kopyalanıp kaydedilir.
- Bellekten kopmuş (sahipsiz) bir envanterde tüm tıklamalar iptal edilir ve ekran kapatılır.
- Menüde hiçbir eşya alınamaz (tıklama, shift, sürükleme, sayı tuşları, F tuşu, çift tık hepsi iptal).
- Çıkışta, ölümde, sunucu kapanışında (`/stop`), `/reload`'da açık depolar kaydedilir; kapanışta tüm kayıtların bitmesi beklenir.
- Otomatik kayıt (varsayılan 3 dk) açık depoları ve başarısız kayıtları kaydeder. Başarısız kayıt olan oyuncu bellekten atılmaz, tekrar denenir.
- Okunamayan (bozuk) bir depo **kilitlenir ve üzerine asla yazılmaz**; konsola ayrıntılı log düşer.
- Yaratıcı mod engeli (creative eşyaların survival'a taşınmasını önler), izleyici modu engeli.
- Yasaklı eşya kontrolü: tıklama, shift-tık, sayı tuşu takası, F tuşu takası ve sürükleme dahil.
- Salt okunur yönetici görünümü (`sanaldepo.admin.duzenle` yoksa).
- Yöneticilerin başkasının deposunu açması konsola loglanır.
- Hileli istemciden envanter açıkken gönderilen komutlar da güvenle işlenir.

## Derleme

```
mvn package
```
Çıktı: `target/SanalDepo-<sürüm>.jar`. main dalına her push GitHub Actions ile derlenir ve pom.xml sürümüyle (`v<sürüm>`) Release olarak yayınlanır / güncellenir.
