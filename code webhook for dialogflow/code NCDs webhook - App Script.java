function doPost(e) {
  try {
    const body = JSON.parse(e.postData.contents);
    const queryResult = body.queryResult || body.result || {};

    const intent =
      queryResult.intent?.displayName ||
      queryResult.metadata?.intentName ||
      "";

    const queryText =
      queryResult.queryText ||
      queryResult.resolvedQuery ||
      "";

    console.log("================================");
    console.log("INTENT:", intent);
    console.log("QUERY TEXT:", queryText);
    console.log("================================");

    // เมนูของแต่ละโรค: ถ้าพิมพ์ชื่อโรคที่ไม่ใช่ 7 โรค ให้ตอบว่าไม่มีข้อมูล
    if (isDiseaseMenuIntent(intent)) {
      if (!isSupportedDiseaseName(queryText)) {
        console.log(">>> โรคนอกเหนือ 7 โรค:", queryText);

         return createOverrideResponse(
          "ขออภัยค่ะ ตอนนี้ระบบให้ข้อมูลได้เฉพาะ 7 โรค ได้แก่\n" +
          "• โรคหัวใจขาดเลือด\n" +
          "• โรคหลอดเลือดสมอง\n" +
          "• โรคความดันโลหิตสูง\n" +
          "• โรคเบาหวาน\n" +
          "• โรคมะเร็ง\n" +
          "• โรคปอดอุดกั้นเรื้อรัง\n" +
          "• โรคหอบหืด\n" +
          "ไม่สามารถให้ข้อมูลโรคอื่นได้ค่ะ กรุณาเลือกโรคจากเมนู หรือพิมพ์ชื่อโรคใหม่อีกครั้งนะคะ"
        );
      }

      return createEmptyResponse();
    }

    if (intent === "Symptom Check") {
      console.log(">>> เข้า Symptom Check");
      return symptomCheck(queryResult, queryText);
    }

    if (intent === "Symptom Continue") {
      console.log(">>> เข้า Symptom Continue");
      return symptomContinue(queryResult, queryText);
    }

    console.log(">>> ไม่พบ Intent");

    return createResponse(
      "ขออภัยค่ะ กรุณาพิมพ์อาการใหม่อีกครั้งนะคะ"
    );

  } catch (error) {
    console.log("!!! ERROR:", error);

    return createResponse(
      "ขออภัยค่ะ กรุณาลองพิมพ์อาการใหม่อีกครั้งนะคะ"
    );
  }
}


// ตรวจสอบว่าข้อความมีคำอาการในระบบอยู่หรือไม่ (เช่น "ปวดหัวมากกก" ผ่านเพราะมีคำว่า "ปวดหัว")
function isValidSymptomText(text) {
  const normalized = normalizeText(text);

  if (!normalized) return false;

  // ข้อความที่ขึ้นต้นด้วย "ไม่" เช่น "ไม่มีไข้" "ไม่ปวดหัว" ถือว่าไม่ใช่อาการ
  // (กันไม่ให้คำปฏิเสธไปถูกนับเป็นอาการ) ถ้าไม่ต้องการ ลบ 3 บรรทัดนี้ได้
  if (normalized.indexOf("ไม่") === 0) {
    return false;
  }

  return symptomGroups.some(function(group) {
    return group.keywords.some(function(keyword) {
      const normalizedKeyword = normalizeText(keyword);

      if (!normalizedKeyword) return false;

      // คำสั้นมาก (ไม่เกิน 2 ตัวอักษร เช่น "ไอ") ต้องอยู่ต้นข้อความ
      // เพื่อไม่ให้ไปจับกลางคำอื่น
      if (normalizedKeyword.length <= 2) {
        return normalized.indexOf(normalizedKeyword) === 0;
      }

      // คำปกติ: มีอยู่ตรงไหนของข้อความก็ผ่าน
      return normalized.includes(normalizedKeyword);
    });
  });
}


// SYMPTOM CHECK รับอาการแรก
function symptomCheck(queryResult, queryText) {
  let symptoms = getSymptoms(queryResult);
  const newSymptom = String(queryText || "").trim();

  console.log(">>> ตรวจสอบอาการแรก:", newSymptom);

  // ตรวจสอบก่อนบันทึก
  if (!isValidSymptomText(newSymptom)) {
    return createResponse(
      "อาการที่กล่าวมายังไม่แน่ชัด รบกวนคนไข้ระบุอาการเพิ่มนะคะ\n\n" +
      "กรุณาพิมพ์อาการทีละอย่าง เช่น ปวดหัว หนาว เหนื่อย หรือปวดท้องค่ะ"
    );
  }

  symptoms.push(newSymptom);

  console.log("อาการทั้งหมด:", symptoms);

  const context = createContext(queryResult, symptoms);

  return createResponseWithContext(
    "บันทึกอาการ \"" + newSymptom + "\" แล้วค่ะ\n\n" +
    "หากมีอาการอื่น สามารถพิมพ์เพิ่มได้เลยค่ะ\n" +
    "หากไม่มีอาการเพิ่มเติม พิมพ์ \"ไม่มี\" ได้เลยค่ะ",
    [context]
  );
}


// SYMPTOM CONTINUE รับอาการเพิ่ม หรือ "ไม่มี"
function symptomContinue(queryResult, queryText) {
  const answer = normalizeText(queryText);
  let symptoms = getSymptoms(queryResult);

  console.log("คำตอบ:", answer);
  console.log("อาการที่เก็บไว้ก่อนเพิ่ม:", symptoms);

  // ถ้าพิมพ์ "ไม่มี" ให้จบการประเมิน
  if (
    answer === "ไม่มี" ||
    answer === "ไม่มีค่ะ" ||
    answer === "ไม่มีครับ" ||
    answer === "ไม่มีแล้ว" ||
    answer === "ไม่มีอาการอื่น" ||
    answer === "ไม่มีอาการอื่นแล้ว" ||
    answer === "ไม่มีอาการเพิ่มเติม" ||
    answer === "ไม่มีเพิ่มเติม" ||
    answer === "แค่นี้" ||
    answer === "พอแล้ว" ||
    answer === "เท่านี้"
  ) {
    console.log(">>> ผู้ใช้จบการประเมิน");
    console.log(">>> อาการทั้งหมด:", JSON.stringify(symptoms));

    const result = calculateDiseaseScore(symptoms);

    console.log(">>> คะแนน:", JSON.stringify(result.scores));
    console.log(">>> โรคคะแนนสูงสุด:", result.topDisease);

    let message = "🩺 ผลประเมินอาการเบื้องต้น\n\n";

    message += "อาการที่คุณระบุ:\n";

    symptoms.forEach(function(symptom) {
      message += "• " + symptom + "\n";
    });

    message += "\n";

    if (result.topDiseaseNumber === 0) {
      message +=
        "อาการที่กล่าวมายังไม่แน่ชัด รบกวนคนไข้ระบุอาการเพิ่มนะคะ\n";
    } else {
      message +=
        "จากอาการที่ระบุมา ระบบพบว่าอาการมีความสอดคล้องกับ\n";

      message += "👉 " + result.topDisease + "\n";
      message += "มากที่สุดค่ะ\n";

      // เพิ่มข้อความประเมินอาการของโรคที่คะแนนสูงสุด
      message += buildDiseaseDetail(result.topDiseaseNumber);
    }

    message += "\n⚠️ ผลนี้เป็นเพียงการประเมินเบื้องต้น";

    const clearContext = clearSymptomContext(queryResult);

    return createResponseWithContext(
      message,
      [clearContext]
    );
  }

  // ถ้าไม่ใช่คำจบ ให้ตรวจอาการก่อนบันทึก
  if (queryText && queryText.trim() !== "") {
    const newSymptom = queryText.trim();

    console.log(">>> ตรวจสอบอาการใหม่:", newSymptom);

    // หากไม่ตรงกับรายการอาการ จะไม่เพิ่มลง Context
    if (!isValidSymptomText(newSymptom)) {
      return createResponseWithContext(
        "ขออภัยค่ะ รบกวนคนไข้ระบุอาการเพิ่ม เช่น ปวดหัว หนาว เหนื่อย หรือปวดท้องค่ะ\n\n" +
        "หากไม่มีอาการเพิ่มเติม พิมพ์ \"ไม่มี\" ได้เลยค่ะ",
        [createContext(queryResult, symptoms)]
      );
    }

    // บันทึกเฉพาะอาการที่ผ่านการตรวจสอบ
    symptoms.push(newSymptom);

    console.log(">>> อาการทั้งหมดหลังเพิ่ม:", symptoms);

    const context = createContext(queryResult, symptoms);

    return createResponseWithContext(
      "บันทึกอาการ \"" + newSymptom + "\" แล้วค่ะ\n\n" +
      "หากมีอาการอื่น สามารถพิมพ์เพิ่มได้เลยค่ะ\n" +
      "หากไม่มีอาการเพิ่มเติม พิมพ์ \"ไม่มี\" ได้เลยค่ะ",
      [context]
    );
  }

  return createResponse(
    "กรุณาพิมพ์อาการ หรือพิมพ์ \"ไม่มี\" หากไม่มีอาการเพิ่มเติมค่ะ"
  );
}


// ตารางอาการ → โรค
const symptomGroups = [
  // ===== คำเฉพาะแต่ละโรค (โรคเดียว) =====

  // เฉพาะ: โรคหัวใจขาดเลือด
  {
    keywords: [
      "หัวใจ", "หัวใจวาย", "โรคหัวใจตีบตัน", "กล้ามเนื้อหัวใจ",
      "ไขมัน", "ออกซิเจน", "ออกสิเจน", "หน้าซีด", "ความเครียด",
      "ไหล่", "หายใจไม่ออก", "อ้วน", "หดตัวหน้าอก"
    ],
    diseases: [1]
  },

  // เฉพาะ: โรคหลอดเลือดสมอง
  {
    keywords: [
      "หลอดเลือดแตก", "เลือดอุดตัน", "ชาซีกแขนขา", "สับสน",
      "สโตรก", "อัมพฤกษ์", "อัมพาต", "ใบหน้าเบี้ยว",
      "หน้าชาครึ่งซีก", "ลิ้นแข็งพูดไม่ออก", "พร่ามัว",
      "ภาพซ้อน", "ภาพซ้อร", "พาบซ้อน", "ภาพส้อน", "ภาพซอ้น", "ภาพซอน",
      "ภาพเบลอ", "ภาพเบอ", "ภาพเบล",
      "มองไม่ชัด", "มองไมชัด", "มองไม่ชด", "มองไม่ชั", "มองไม่ชัดด",
      "การกลืน", "กลืน"
    ],
    diseases: [2]
  },

  // เฉพาะ: โรคความดันโลหิตสูง
  {
    keywords: [
      "ความดันโลหิตสูง", "ความดัน", "แรงดันสมอง", "เครียด",
      "เลือดแดง", "โรคหัวใจ", "เจ็บตา"
    ],
    diseases: [3]
  },

  // เฉพาะ: โรคเบาหวาน
  {
    keywords: [
      "เลือดออก", "เลิอดออก", "เลือดแอก", "เลือดออด",
      "แผล", "เเผล", "แผลหายช้า", "หนอง", "คล้ำ",
      "ปวดแสบปวดร้อน", "หน้าเบี้ยว", "หน้าบวม", "ปากบวม",
      "ลมหายใจหอม", "หมดสติ", "หมดสะติ",
      "อ่อนแรง", "อ่อนเเรง", "ออนแรง"
    ],
    diseases: [4]
  },

  // เฉพาะ: มะเร็ง
  {
    keywords: [
      "มะเร็ง", "แสงแดด", "ผิวหนัง", "ฝุ่น", "ลำคอ", "ไอเสียงแหบ",
      "ไข้", "ไข้สูง", "ใข้", "หนาว", "หนาวสั่น",
      "ท้องเสีย", "ทองเสีย", "ท้องเสี", "ท้องผูก", "ปวดหลัง"
    ],
    diseases: [5]
  },

  // เฉพาะ: โรคปอดอุดกั้นเรื้อรัง (COPD)
  {
    keywords: [
      "หายใจเหนื่อย", "เสมหะสีใส", "เสมหะสีเข้ม", "พ่นยา", "วี้ด",
      "กรดไหลย้อน", "เชื้อรา", "ปลายนิ้วคล้ำ", "กระสับกระส่าย",
      "อุจจาระมีเลือดปน",
      "ระคายเคือง", "ระคายเคืองง", "ระคานเคือง", "ระคายเคืือง",
      "ระคายเคิอง", "ละคายเคือง"
    ],
    diseases: [6]
  },

  // เฉพาะ: โรคหอบหืด
  {
    keywords: [
      "หอบหืด", "แพ้", "เเพ้", "เสมหะ", "หายใจเสียงแหลม",
      "ระคายเคืองสารเคมี", "ปากคล้ำ", "ซี่โครงนูน",
      "มีเสียงหวีดในอก", "แพ้เกสรดอกไม้"
    ],
    diseases: [7]
  },

  // ===== คำที่ใช้ร่วมกันหลายโรค =====

  // หลอดเลือด / วิงเวียน → หัวใจ, ความดัน
  {
    keywords: [
      "หลอดเลือด",
      "วิงเวียน", "วิงเวียนศีรษะ", "วิงเวียนศรีษะ",
      "เวียนศีรษะ", "เวียนศรีษะ", "เวียนสีสะ",
      "เวียนหัว", "เวียนหัง", "เวียนหัส"
    ],
    diseases: [1, 3]
  },

  // ความดันโลหิต → หัวใจ, หลอดเลือดสมอง
  {
    keywords: ["ความดันโลหิต"],
    diseases: [1, 2]
  },

  // ปวดศีรษะ → หลอดเลือดสมอง, ความดัน, มะเร็ง
  {
    keywords: [
      "ปวดศีรษะ", "ปวดหัว", "ปวดหัง", "ปดหัว",
      "ปวดหัส", "ปวดห้ว", "ปวสหัว"
    ],
    diseases: [2, 3, 5]
  },

  // มึนงง / วูบ → หลอดเลือดสมอง, ความดัน
  {
    keywords: [
      "มึนงง", "วูบ", "วูล", "วุบ", "วูป",
      "มองไม่เห็น"
    ],
    diseases: [2, 3]
  },

  // เหนื่อย → หัวใจ, ความดัน, เบาหวาน
  {
    keywords: ["เหนื่อย", "เหนืย", "เหนื่อ", "เหนย"],
    diseases: [1, 3, 4]
  },

  // หอบ / ไอ → หัวใจ, COPD
  {
    keywords: ["หอบ", "หอบบ", "ไอ", "ใอ", "ไออ"],
    diseases: [1, 6]
  },

  // หายใจหอบ → ความดัน, เบาหวาน
  {
    keywords: ["หายใจหอบ", "หายใจหอบบ"],
    diseases: [3, 4]
  },

  // เหนื่อยหอบ → มะเร็ง, หอบหืด
  {
    keywords: ["เหนื่อยหอบ"],
    diseases: [5, 7]
  },

  // แน่นหน้าอก → หัวใจ, มะเร็ง, COPD, หอบหืด
  {
    keywords: [
      "แน่นหน้าอก", "เเน่นหน้าอก", "แน่นหนาอก",
      "แนนหน้าอก", "แน่นหน้าอ"
    ],
    diseases: [1, 5, 6, 7]
  },

  // คลื่นไส้ → หัวใจ, ความดัน, เบาหวาน, มะเร็ง
  {
    keywords: [
      "คลื่นไส้", "คลื่นไส", "คลื่นใส", "คลิ่นไส้",
      "คลื่นใส้", "คลื่นไสอ", "อ้วก", "อ้วส", "อ้วด", "อวก"
    ],
    diseases: [1, 3, 4, 5]
  },

  // อาเจียน → หัวใจ, หลอดเลือดสมอง, ความดัน, เบาหวาน, มะเร็ง
  {
    keywords: [
      "อาเจียน", "าเจียน", "อาเจิยน", "อาเจียร",
      "อาเจีน", "อาเจยีน"
    ],
    diseases: [1, 2, 3, 4, 5]
  },

  // เหงื่อ → หัวใจ, เบาหวาน, มะเร็ง
  {
    keywords: ["เหงื่อ", "เหงิ่อ", "เงื่อ", "เงิ่อ"],
    diseases: [1, 4, 5]
  },

  // หน้ามืด → ความดัน, เบาหวาน
  {
    keywords: ["หน้ามืด", "หน่ามืด", "หน้ามึด"],
    diseases: [3, 4]
  },

  // เพลีย / ปวดท้อง → เบาหวาน, มะเร็ง
  {
    keywords: [
      "เพลีย", "อ่อนเพลีย", "อ่อนเพลี", "ออนเพลีย",
      "ปวดท้อง", "ปวสท้อง", "ปวดทอง", "ปวดทอ้ง", "ปดท้อง"
    ],
    diseases: [4, 5]
  },

  // เลือดกำเดา / ปอด → ความดัน, มะเร็ง
  {
    keywords: [
      "เลือดกำเดา", "เลือดกำเดาไหล", "เลือดกําเดา", "เลือดกําเดาไหล",
      "ปอด"
    ],
    diseases: [3, 5]
  },

  // ชัก → ความดัน, เบาหวาน
  {
    keywords: [
      "ชัก", "ขัก", "ชักเกร็ง", "ชักเกง", "ชักเกร๊ง", "ชักเกรง"
    ],
    diseases: [3, 4]
  },

  // ใจเต้น → ความดัน, COPD
  {
    keywords: ["ใจเต้น", "ใจเต็", "ใจเต้นน", "ใจเตน"],
    diseases: [3, 6]
  },

  // ใจสั่น → เบาหวาน, มะเร็ง, COPD
  {
    keywords: ["ใจสั่น", "ใจสัน", "ใจสั่นน", "ใจสั่่น"],
    diseases: [4, 5, 6]
  },

  // บวม → ความดัน, เบาหวาน, หอบหืด
  {
    keywords: ["บวม", "บสม", "บวใ"],
    diseases: [3, 4, 7]
  },

  // ติดเชื้อ → เบาหวาน, COPD, หอบหืด
  {
    keywords: ["ติดเชื้อ"],
    diseases: [4, 6, 7]
  },

  // อักเสบ → เบาหวาน, มะเร็ง, COPD, หอบหืด
  {
    keywords: ["อักเสบ", "อักเส", "อักเสป", "อัดเสบ"],
    diseases: [4, 5, 6, 7]
  },

  // ภูมิแพ้ / ยาพ่น / ปลายมือ-เท้าคล้ำ → COPD, หอบหืด
  {
    keywords: [
      "ภูมิแพ้", "ยาพ่น", "ยาพ้น",
      "ปลายมือคล้ำ", "ปลายเท้าคล้ำ"
    ],
    diseases: [6, 7]
  }
];


// ชื่อโรค
const diseases = {
  1: "โรคหัวใจขาดเลือด",
  2: "โรคหลอดเลือดสมอง",
  3: "โรคความดันโลหิตสูง",
  4: "โรคเบาหวาน",
  5: "มะเร็ง",
  6: "โรคปอดอุดกั้นเรื้อรัง (COPD)",
  7: "โรคหอบหืด"
};


// ===== ตรวจชื่อโรคในเมนูของแต่ละโรค (กันโรคนอกเหนือ 7 โรค) =====

// Intent เมนูของแต่ละโรคใน Dialogflow ตั้งชื่อลงท้ายด้วย " Menu" (เช่น "Hypertension Menu")
// โค้ดจะตรวจทุก Intent ที่ชื่อลงท้ายแบบนี้อัตโนมัติ ไม่ต้องใส่ทีละชื่อ
// (ต้องเปิด Enable webhook call ที่ Intent เมนูของแต่ละโรคด้วย)
const diseaseMenuSuffix = " Menu";

// Intent ที่ชื่อลงท้าย " Menu" แต่ไม่ใช่เมนูของโรค (ไม่ต้องตรวจชื่อโรค) เช่น เมนูหลัก
const nonDiseaseMenuIntents = [
  "Main Menu"
];

// Intent นี้เป็นเมนูของโรคหรือไม่
function isDiseaseMenuIntent(intentName) {
  const name = String(intentName || "");

  if (nonDiseaseMenuIntents.indexOf(name) !== -1) return false;

  return name.length > diseaseMenuSuffix.length &&
    name.slice(-diseaseMenuSuffix.length) === diseaseMenuSuffix;
}

// ชื่อโรคและชื่อเรียกอื่นที่ยอมรับ (เพิ่มคำได้ แต่อย่าใส่คำกว้างๆ เช่น "ความดัน" เพราะจะรับ "ความดันต่ำ" ด้วย)
const supportedDiseaseNames = [
  "หัวใจขาดเลือด", "กล้ามเนื้อหัวใจขาดเลือด",
  "หลอดเลือดสมอง", "อัมพฤกษ์", "อัมพาต", "สโตรก",
  "ความดันโลหิตสูง", "ความดันสูง",
  "เบาหวาน",
  "มะเร็ง",
  "ปอดอุดกั้น", "copd",
  "หอบหืด", "หืด"
];

// ข้อความที่พิมพ์มา เป็นชื่อโรคใน 7 โรคหรือไม่
function isSupportedDiseaseName(text) {
  const typed = normalizeText(text);

  if (!typed) return false;

  return supportedDiseaseNames.some(function(name) {
    return typed.includes(normalizeText(name));
  });
}


// รายละเอียดการประเมินอาการของแต่ละโรค
const diseaseDetails = {
  1: {
    title: "ประเมินอาการของโรคหัวใจขาดเลือด",
    mild: [
      "เจ็บ แน่น หรือบีบรัดบริเวณกลางหน้าอก เฉพาะเวลาออกแรงมาก",
      "ไม่มีอาการเหงื่อแตก หายใจหอบเหนื่อย หน้ามืด หรือเจ็บแน่นรุนแรงขณะนั่งพักเฉยๆ"
    ],
    severe: [
      "เจ็บแน่นหน้าอกรุนแรงเฉียบพลัน แม้จะได้พักหรือใช้ยาอมใต้ลิ้น",
      "หายใจหอบเหนื่อย เหงื่อแตก เวียนศีรษะ คลื่นไส้อาเจียน"
    ]
  },

  2: {
    title: "ประเมินอาการของโรคหลอดเลือดสมอง",
    mild: [
      "หน้าเบี้ยว",
      "แขนขาอ่อนแรงครึ่งซีก",
      "พูดไม่ชัด"
    ],
    severe: [
      "ปวดศีรษะรุนแรงเฉียบพลัน",
      "ไม่รู้สึกตัว ตอบสนองช้าลง หรือปลุกไม่ตื่น",
      "ชักเกร็ง",
      "หายใจผิดปกติ หายใจติดขัด"
    ]
  },

  3: {
    title: "ประเมินอาการของโรคความดันโลหิตสูง",
    mild: [
      "วัดความดันตามปกติ ไม่มีอาการผิดปกติใดๆ",
      "มีอาการตึงๆ บริเวณท้ายทอย",
      "เวียนศีรษะเล็กน้อย ปวดหัวมึนๆ ช่วงเช้า"
    ],
    severe: [
      "ค่าระดับความดันโลหิตตัวบนตั้งแต่ 180 mmHg ขึ้นไป",
      "ปวดศีรษะรุนแรงอย่างกะทันหัน",
      "แน่นหน้าอกอย่างรุนแรง",
      "เลือดกำเดาไหลไม่หยุด"
    ]
  },

  4: {
    title: "ประเมินอาการของโรคเบาหวาน",
    mild: [
      "หิวน้ำบ่อย ปัสสาวะบ่อย โดยเฉพาะตอนกลางคืน",
      "มีอาการมือสั่น หรือเวียนศีรษะ แต่ยังรู้สึกตัวดีและกลืนอาหารได้",
      "รู้สึกหิวจัด หรือรู้สึกหงุดหงิด"
    ],
    severe: [
      "ลมหายใจมีกลิ่นคล้ายกับผลไม้",
      "มีอาการชักเกร็ง",
      "แผลที่เท้าอักเสบ มีกลิ่นเหม็น",
      "ไข้สูงหนาวสั่น ตาพร่ามัวลงเฉียบพลัน"
    ]
  },

  5: {
    title: "ประเมินอาการของโรคมะเร็ง",
    mild: [
      "คลื่นไส้ อาเจียน เล็กน้อยหลังให้ยาเคมีบำบัด",
      "เบื่ออาหาร หรือลิ้มรสอาหารเปลี่ยนไป",
      "ผิวแห้ง คัน คล้ำขึ้น หรือแห้งลอก",
      "ถ่ายเหลว 1-2 ครั้ง/วัน"
    ],
    severe: [
      "มีไข้สูง ตั้งแต่ 38°C ขึ้นไป",
      "ปวดหลังรุนแรง",
      "เลือดกำเดาไหลไม่หยุด",
      "อาเจียนหนัก หรือท้องเสียรุนแรง"
    ]
  },

  6: {
    title: "ประเมินอาการของโรคปอดอุดกั้นเรื้อรัง",
    mild: [
      "เหนื่อยหอบเฉพาะเวลาออกแรงมาก",
      "เสมหะมีลักษณะขาวใสหรือเหลืองอ่อน",
      "ได้ยินเสียงหวีดในปอดบางครั้งเมื่อออกแรง",
      "ถ่ายเหลว 1-2 ครั้ง/วัน"
    ],
    severe: [
      "เหนื่อยหอบรุนแรงเฉียบพลัน",
      "มีไข้สูง ริมฝีปากหรือปลายมือปลายเท้าเขียวคล้ำ",
      "พ่นยาขยายหลอดเลือดชนิดออกฤทธิ์เร็วแล้วอาการไม่ดีขึ้น"
    ]
  },

  7: {
    title: "ประเมินอาการของโรคหอบหืด",
    mild: [
      "มีอาการไอแห้งๆ หรือไอมีเสมหะใส",
      "สามารถพูดคุยเป็นประโยคยาวๆ ได้โดยไม่ต้องหยุดพัก",
      "หลังพ่นยา อาการแน่นหน้าอกหรือเสียงหวีดลดลง และหายไป"
    ],
    severe: [
      "เหนื่อยหอบรุนแรงเฉียบพลัน",
      "เห็นซี่โครงชัดเจนขณะหายใจเข้า",
      "พ่นยาขยายหลอดเลือดแล้วอาการไม่บรรเทา"
    ]
  }
};


// สร้างข้อความรายละเอียดการประเมินอาการของโรค
function buildDiseaseDetail(diseaseNumber) {
  const d = diseaseDetails[diseaseNumber];
  if (!d) return "";

  let text = "\n" + d.title + "\n\n";

  text += "🟢 ลักษณะอาการที่ไม่รุนแรง ดูแลและปรับพฤติกรรมตนเองได้\n";
  d.mild.forEach(function(line) {
    text += "• " + line + "\n";
  });

  text += "\n🔴 ลักษณะอาการที่รุนแรงควรไปพบแพทย์\n";
  d.severe.forEach(function(line) {
    text += "• " + line + "\n";
  });

  text += "\n📞 โทรสายด่วนโรงพยาบาลและเหตุฉุกเฉิน 1669\n";

  return text;
}


// คำนวณคะแนน
function calculateDiseaseScore(symptoms) {
  const scores = {
    1: 0,
    2: 0,
    3: 0,
    4: 0,
    5: 0,
    6: 0,
    7: 0
  };

  // คะแนนจาก "กลุ่มเฉพาะโรค" (กลุ่มคำที่ผูกกับโรคเดียว) ใช้ตัดสินเมื่อคะแนนรวมเสมอกัน
  const specificScores = {
    1: 0,
    2: 0,
    3: 0,
    4: 0,
    5: 0,
    6: 0,
    7: 0
  };

  symptoms.forEach(function(symptom) {
    const text = normalizeText(symptom);

    console.log("กำลังตรวจอาการ:", text);

    symptomGroups.forEach(function(group) {
      let matched = false;

      group.keywords.forEach(function(keyword) {
        const normalizedKeyword = normalizeText(keyword);

        if (text.includes(normalizedKeyword)) {
          matched = true;
        }
      });

      if (matched) {
        console.log("พบกลุ่มอาการ:", group.keywords.join(", "));

        group.diseases.forEach(function(diseaseNumber) {
          scores[diseaseNumber] += 1;

          if (group.diseases.length === 1) {
            specificScores[diseaseNumber] += 1;
          }
        });
      }
    });
  });

  let highestScore = 0;
  let topDiseaseNumber = 0;

  for (let number = 1; number <= 7; number++) {
    if (scores[number] > highestScore) {
      highestScore = scores[number];
      topDiseaseNumber = number;
    } else if (
      scores[number] === highestScore &&
      highestScore > 0 &&
      specificScores[number] > specificScores[topDiseaseNumber]
    ) {
      // คะแนนรวมเสมอกัน → เลือกโรคที่มีคำเฉพาะโรคตรงมากกว่า
      topDiseaseNumber = number;
    }
  }

  console.log(">>> คะแนนเฉพาะโรค:", JSON.stringify(specificScores));

  if (highestScore === 0) {
    return {
      scores: scores,
      topDiseaseNumber: 0,
      topDisease: "ไม่สามารถระบุได้จากอาการที่ระบุ"
    };
  }

  return {
    scores: scores,
    topDiseaseNumber: topDiseaseNumber,
    topDisease: diseases[topDiseaseNumber]
  };
}


// ดึงอาการจาก Context
function getSymptoms(queryResult) {
  const contexts =
    queryResult.outputContexts ||
    queryResult.contexts ||
    [];

  console.log("OUTPUT CONTEXTS:", JSON.stringify(contexts));

  for (let i = 0; i < contexts.length; i++) {
    const contextName = contexts[i].name || "";

    if (contextName.toLowerCase().includes("symptom-check")) {
      const parameters = contexts[i].parameters || {};

      if (
        parameters.symptoms &&
        Array.isArray(parameters.symptoms)
      ) {
        return parameters.symptoms;
      }
    }
  }

  return [];
}


// สร้าง Context
function createContext(queryResult, symptoms) {
  const contexts =
    queryResult.outputContexts ||
    queryResult.contexts ||
    [];

  let contextName = "";

  for (let i = 0; i < contexts.length; i++) {
    const name = contexts[i].name || "";

    if (name.toLowerCase().includes("symptom-check")) {
      contextName = name;
      break;
    }
  }

  if (contextName === "") {
    console.log("!!! ไม่พบ symptom-check context");
  }

  return {
    name: contextName,
    lifespanCount: 10,
    parameters: {
      symptoms: symptoms
    }
  };
}


// ล้าง Context
function clearSymptomContext(queryResult) {
  const contexts =
    queryResult.outputContexts ||
    queryResult.contexts ||
    [];

  for (let i = 0; i < contexts.length; i++) {
    const name = contexts[i].name || "";

    if (name.toLowerCase().includes("symptom-check")) {
      return {
        name: name,
        lifespanCount: 0
      };
    }
  }

  return {
    name: "",
    lifespanCount: 0
  };
}


// Normalize ข้อความ
function normalizeText(text) {
  if (!text) {
    return "";
  }

  return String(text)
    .trim()
    .replace(/\s+/g, "")
    .toLowerCase();
}


// Response ว่าง (ให้ Dialogflow ใช้ข้อความที่ตั้งไว้ใน Intent ตามปกติ)
function createEmptyResponse() {
  return ContentService
    .createTextOutput(JSON.stringify({}))
    .setMimeType(ContentService.MimeType.JSON);
}


// Response ที่ทับการ์ด/ข้อความที่ตั้งไว้ใน Intent ทั้งหมด (ใช้ตอนปฏิเสธโรคนอก 7 โรค)
function createOverrideResponse(text) {
  return ContentService
    .createTextOutput(
      JSON.stringify({
        fulfillmentText: text,
        fulfillmentMessages: [
          { text: { text: [text] } }
        ]
      })
    )
    .setMimeType(ContentService.MimeType.JSON);
}


// Response ธรรมดา
function createResponse(text) {
  return ContentService
    .createTextOutput(
      JSON.stringify({
        fulfillmentText: text
      })
    )
    .setMimeType(ContentService.MimeType.JSON);
}


// Response พร้อม Context
function createResponseWithContext(text, contexts) {
  return ContentService
    .createTextOutput(
      JSON.stringify({
        fulfillmentText: text,
        outputContexts: contexts
      })
    )
    .setMimeType(ContentService.MimeType.JSON);
}
