// Turkish names for models and fields that have no key in the original locale files.

export const MODEL_TR = {
  LedgerAccount: 'Hesap planı', JournalEntry: 'Yevmiye fişi', JournalLine: 'Yevmiye satırı',
  AssetCategory: 'Demirbaş kategorisi', AssetLocation: 'Demirbaş lokasyonu', AssetAssignment: 'Zimmet', AssetMaintenance: 'Bakım kaydı',
  AssetCountLine: 'Sayım satırı', ShiftAssignment: 'Vardiya ataması', AttendanceRecord: 'Mesai kaydı',
  BudgetLine: 'Bütçe satırı', BudgetEvent: 'Bütçe olayı', BudgetAlert: 'Bütçe uyarısı', ContractEvent: 'Sözleşme olayı',
  EInvoiceEvent: 'e-Fatura olayı', ExpenseCategory: 'Masraf türü', ExpenseLine: 'Masraf satırı',
  TreasuryTransaction: 'Kasa/banka hareketi', PartnerPayment: 'Tahsilat/Ödeme', PaymentAllocation: 'Ödeme eşleştirme',
  ChequeEvent: 'Çek/senet hareketi', Vehicle: 'Araçlar', VehicleAssignment: 'Araç tahsisi', VehicleOdometerReading: 'Kilometre kaydı',
  VehicleFuelEntry: 'Yakıt kaydı', VehicleService: 'Servis kaydı', VehicleDocument: 'Araç belgesi',
  LeaveType: 'İzin türleri', LeaveBalance: 'İzin bakiyeleri', Holiday: 'Resmi tatiller', UserCompany: 'Şirket üyelikleri',
  Permission: 'İzinler', UserRole: 'Kullanıcı rolleri', ProductCategory: 'Ürün kategorileri', ProductVariant: 'Ürün varyantları',
  Warehouse: 'Depolar', StockBalance: 'Stok bakiyeleri', StockLot: 'Lotlar', LotBalance: 'Lot bakiyeleri', SerialNumber: 'Seri numaraları',
  StockMovementLine: 'Hareket satırları', StockMovementSerial: 'Hareket seri numaraları', StockTransaction: 'Stok hareket defteri',
  StockCountLine: 'Sayım satırları', BusinessPartner: 'Cari hesaplar', PartnerLedgerEntry: 'Cari hareketler',
  SalaryRecord: 'Ücret kayıtları', PayrollParameter: 'Bordro parametreleri', Payslip: 'Bordro pusulaları', PayslipItem: 'Pusula kalemleri',
  ReviewCycleCompetency: 'Dönem yetkinlikleri', PerformanceReview: 'Performans değerlendirmeleri', PerformanceGoal: 'Hedefler',
  CompetencyRating: 'Yetkinlik puanları', AuditLog: 'Denetim kaydı', FileObject: 'Dosyalar', NumberSequence: 'Numara serileri',
  BillOfMaterials: 'Ürün ağaçları', BomLine: 'Ürün ağacı satırları', WorkOrderComponent: 'İş emri bileşenleri',
  ProductionRecord: 'Üretim kayıtları', ProjectMember: 'Proje ekibi', ProjectTask: 'Görevler', TimeEntry: 'Zaman kayıtları',
  PurchaseRequestLine: 'Talep satırları', SupplierQuoteLine: 'Teklif satırları', PurchaseOrderLine: 'Sipariş satırları',
  GoodsReceiptLine: 'Mal kabul satırları', PurchaseInvoiceLine: 'Fatura satırları', JobApplication: 'Başvurular',
  ApplicationActivity: 'Başvuru hareketleri', Interview: 'Mülakatlar', InterviewParticipant: 'Mülakat katılımcıları', JobOffer: 'İş teklifleri',
  SalesQuoteLine: 'Teklif satırları', SalesOrderLine: 'Sipariş satırları', StockReservation: 'Stok rezervasyonları',
  DeliveryNote: 'Sevk irsaliyeleri', DeliveryNoteLine: 'İrsaliye satırları', SalesInvoiceLine: 'Fatura satırları',
  SalesReturnLine: 'İade satırları', Currency: 'Para birimleri', ExchangeRate: 'Döviz kurları', TaxRate: 'Vergi oranları', Unit: 'Birimler',
  TicketCategory: 'Destek kategorileri', TicketSlaPolicy: 'SLA politikaları', Ticket: 'Destek talepleri', TicketComment: 'Yorumlar',
  TicketEvent: 'Talep olayları', TrainingEnrollment: 'Eğitim katılımları', WorkflowDefinition: 'Akış tanımları',
  WorkflowStep: 'Akış adımları', WorkflowInstance: 'Onay süreçleri', WorkflowAction: 'Onay işlemleri',
};

export const FIELD_TR = {
  entryDate: 'Kayıt tarihi', sourceNumber: 'Kaynak belge', model: 'Model', conditionOut: 'Teslim durumu', acknowledgedAt: 'Onaylanma',
  returnNotes: 'İade notu', countDate: 'Sayım tarihi', foundAt: 'Bulunma', categoryIds: 'Kategoriler', level: 'Seviye',
  remindedFor: 'Hatırlatılan', activatedAt: 'Aktifleşme', fromValue: 'Önceki değer', toValue: 'Yeni değer',
  expectedCloseDate: 'Tahmini kapanış', profile: 'Profil', uuid: 'ETTN', lineExtensionAmount: 'Mal/hizmet tutarı',
  allowanceTotal: 'İskonto toplamı', payableAmount: 'Ödenecek tutar', xml: 'XML', statusCode: 'Durum kodu', responseNote: 'Yanıt notu',
  sentAt: 'Gönderim', deliveredAt: 'Teslim', message: 'Mesaj', limitAmount: 'Limit', totalAmount: 'Toplam tutar', paidAt: 'Ödeme tarihi',
  expenseDate: 'Masraf tarihi', iban: 'IBAN', txDate: 'İşlem tarihi', documentNumber: 'Belge no', amountIn: 'Giriş', amountOut: 'Çıkış',
  eventDate: 'Olay tarihi', fromStatus: 'Önceki durum', toStatus: 'Yeni durum', ownership: 'Mülkiyet', serviceRemindedFor: 'Servis hatırlatması',
  retiredOn: 'Kullanımdan kalkış', endOdometer: 'Bitiş km', readingDate: 'Okuma tarihi', serviceDate: 'Servis tarihi',
  remindedState: 'Hatırlatma durumu', leaveTypeId: 'İzin türü', entitledDays: 'Hak edilen gün', usedDays: 'Kullanılan gün',
  trackingType: 'Takip türü', lotId: 'Lot', movementDate: 'Hareket tarihi', expectedQty: 'Beklenen miktar', debitBase: 'Borç (yerel)',
  creditBase: 'Alacak (yerel)', grossSalary: 'Brüt ücret', sgkEmployeeRate: 'SGK işçi payı', unemploymentEmployeeRate: 'İşsizlik işçi payı',
  sgkEmployerRate: 'SGK işveren payı', unemploymentEmployerRate: 'İşsizlik işveren payı', stampTaxRate: 'Damga vergisi oranı',
  taxBrackets: 'Vergi dilimleri', grossTotal: 'Brüt toplam', deductionTotal: 'Kesinti toplamı', employerCostTotal: 'İşveren maliyeti',
  employeeName: 'Personel adı', departmentName: 'Departman', positionName: 'Pozisyon', employedDays: 'Çalışılan gün',
  extraEarnings: 'Ek kazançlar', grossPay: 'Brüt ödeme', uploadedById: 'Yükleyen', overheadCost: 'Genel gider', scrapRate: 'Fire oranı',
  producedQty: 'Üretilen miktar', quantityPer: 'Birim başına miktar', recordDate: 'Kayıt tarihi', materialCost: 'Malzeme maliyeti',
  budgetAmount: 'Bütçe tutarı', taskNo: 'Görev no', workDate: 'Çalışma tarihi', costAmount: 'Maliyet', requestDate: 'Talep tarihi',
  quoteDate: 'Teklif tarihi', receivedQty: 'Teslim alınan', invoicedQty: 'Faturalanan', receiptDate: 'Kabul tarihi', serialNos: 'Seri numaraları',
  paymentStatus: 'Ödeme durumu', linkedinUrl: 'LinkedIn', stageChangedAt: 'Aşama değişimi', hiredAt: 'İşe alım tarihi',
  fromStage: 'Önceki aşama', toStage: 'Yeni aşama', durationMinutes: 'Süre (dk)', interviewId: 'Mülakat', deliveredQty: 'Sevk edilen',
  returnedQty: 'İade edilen', firstResponseHours: 'İlk yanıt (saat)', firstResponseDueAt: 'İlk yanıt hedefi', resolutionDueAt: 'Çözüm hedefi',
  firstRespondedAt: 'İlk yanıt', pausedAt: 'Duraklatma', pausedMinutes: 'Duraklama (dk)', satisfactionRating: 'Memnuniyet puanı',
  satisfactionComment: 'Memnuniyet yorumu', ticketId: 'Talep', isInternal: 'Dahili', format: 'Format', isMandatory: 'Zorunlu',
  certificateExpiresAt: 'Sertifika bitişi', feedbackRating: 'Geri bildirim puanı', feedbackAt: 'Geri bildirim tarihi',
  approverUserId: 'Onaycı kullanıcı', startedById: 'Başlatan', stepOrder: 'Adım', approverRoleId: 'Onaycı rol',
};

const WORDS = {
  date: 'tarihi', at: '', amount: 'tutarı', total: 'toplam', qty: 'miktar', id: '', name: 'adı', number: 'no', count: 'sayı',
  rate: 'oran', days: 'gün', hours: 'saat', minutes: 'dakika', cost: 'maliyet', price: 'fiyat', status: 'durum', type: 'tür',
};

/** Last resort for unknown camelCase names: translate known words, keep the rest. */
export function trWords(name) {
  const parts = name.replace(/Id$/, '').replace(/([a-z0-9])([A-Z])/g, '$1 $2').toLowerCase().split(' ');
  const out = parts.map((p) => WORDS[p] ?? p).filter(Boolean).join(' ');
  return out.charAt(0).toUpperCase() + out.slice(1);
}
