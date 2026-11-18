## SanalDepo v1.0.0

İlk sürüm. `SanalDepo-1.0.0.jar` dosyasını sunucunun `plugins` klasörüne atın.

**Uyumluluk:** Spigot / Paper / Purpur 1.16.5 – 1.21.x · Java 16+

### Özellikler
- `/depo` → 54 slotluk menü: dama desenli cam kenarlar, renkli köşeler, ortada 1'den 28'e sıralı sandık simgeleri (adet = depo numarası)
- Her depo 54 slot; doluluk sayısı, yüzde ve renkli doluluk çubuğu menüde görünür
- `/depo <numara>` → depoyu direkt açar
- `/depo <oyuncu>` ve `/depo <oyuncu> <numara>` → yöneticiler başka oyuncunun depolarını görür (çevrimdışı oyuncular dahil); düzenleme ayrı yetki
- Depo sayısı `sanaldepo.depo.<sayı>` yetkisiyle; `maksimum-hak` OP dahil kimse tarafından aşılamaz (en fazla 28)
- SQLite (varsayılan, WAL) veya MySQL/MariaDB (HikariCP); oyuncu başına tek sorgu, sadece değişen depo yazılır, boş depo için satır tutulmaz
- Tüm veritabanı işlemleri tek IO iş parçacığında sırayla, ana iş parçacığı hiç beklemez
- Dupe koruması: aynı depo için tek canlı envanter (sahip + yönetici aynı envanteri görür), sahipsiz envanterde tıklama yok, çıkış / kapanış / çökme senaryolarında kayıt
- Okunamayan depo kilitlenir, üzerine asla yazılmaz
- Yasaklı eşyalar, engelli dünyalar, yaratıcı mod engeli, açma bekleme süresi, menüye geri dönüş, sesler — hepsi config'den
