/*
 * Copyright (C) 2010-2026 Tianjin Langhua Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.langhua.ofbiz.mfg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.Test;

/**
 * The engineering side: the BOM version identifiers, the development tracks and the stage ladder,
 * the change orders, the attachment types and the two BOM formats.
 */
public class MfgProductRulesTest {

    @Test
    public void numbersTheNextBomVersion() {
        List<String> none = new java.util.ArrayList<>();
        assertEquals(1L, MfgProductServices.nextBomVersionNo(none, "BT5"));

        List<String> one = java.util.Arrays.asList("BT5-V1");
        assertEquals(2L, MfgProductServices.nextBomVersionNo(one, "BT5"));

        // gaps do not matter, the highest number wins
        List<String> gap = java.util.Arrays.asList("BT5-V1", "BT5-V3", "BT5-V2");
        assertEquals(4L, MfgProductServices.nextBomVersionNo(gap, "BT5"));

        // versions of another model are ignored, and a suffix that is not a number is skipped
        List<String> mixed = java.util.Arrays.asList("BT6-V9", "BT5-VX");
        assertEquals(1L, MfgProductServices.nextBomVersionNo(mixed, "BT5"));
    }

    @Test
    public void walksTheDevelopmentStageLadder() {
        assertEquals("MFG_STAGE_DESIGN", MfgDevServices.nextDevStage("MFG_STAGE_CONCEPT"));
        assertEquals("MFG_STAGE_PROTOTYPE", MfgDevServices.nextDevStage("MFG_STAGE_DESIGN"));
        assertEquals("MFG_STAGE_TRIAL", MfgDevServices.nextDevStage("MFG_STAGE_PROTOTYPE"));
        assertEquals("MFG_STAGE_MASS", MfgDevServices.nextDevStage("MFG_STAGE_TRIAL"));
        // mass production is the last stage
        assertNull(MfgDevServices.nextDevStage("MFG_STAGE_MASS"));
        assertNull(MfgDevServices.nextDevStage("NOT_A_STAGE"));
        assertNull(MfgDevServices.nextDevStage(null));

        assertEquals(0, MfgDevServices.devStageIndex("MFG_STAGE_CONCEPT"));
        assertEquals(4, MfgDevServices.devStageIndex("MFG_STAGE_MASS"));
        assertEquals(-1, MfgDevServices.devStageIndex("NOT_A_STAGE"));
    }

    @Test
    public void onlyAPassMovesTheProjectOn() {
        assertTrue(MfgDevServices.stageAdvancesOn("MFG_REVIEW_PASS"));
        assertTrue(MfgDevServices.stageAdvancesOn("MFG_REVIEW_CONDITIONAL"));
        assertTrue(!MfgDevServices.stageAdvancesOn("MFG_REVIEW_FAIL"));
        assertTrue(!MfgDevServices.stageAdvancesOn(null));
    }

    @Test
    public void knowsTheDevelopmentAndChangeOrderCodes() {
        assertTrue(MfgDevServices.isKnownDevStage("MFG_STAGE_TRIAL"));
        assertTrue(!MfgDevServices.isKnownDevStage("MFG_STAGE_SOMETHING"));

        assertTrue(MfgDevServices.isKnownReviewResult("MFG_REVIEW_CONDITIONAL"));
        assertTrue(!MfgDevServices.isKnownReviewResult("PASS"));

        assertTrue(MfgDevServices.isKnownDevStatus("MFG_DEV_ONHOLD"));
        assertTrue(!MfgDevServices.isKnownDevStatus("MFG_DEV_BOGUS"));

        assertTrue(MfgDevServices.isKnownEcoStatus("MFG_ECO_APPLIED"));
        assertTrue(!MfgDevServices.isKnownEcoStatus("DRAFT"));

        assertTrue(MfgDevServices.isKnownEcoType("MFG_ECO_MATERIAL"));
        assertTrue(!MfgDevServices.isKnownEcoType("MFG_ECO_PRICE"));
    }

    @Test
    public void appliesOnlyAnApprovedChangeOrder() {
        assertTrue(MfgDevServices.ecoAppliable("MFG_ECO_APPROVED"));
        assertTrue(!MfgDevServices.ecoAppliable("MFG_ECO_DRAFT"));
        assertTrue(!MfgDevServices.ecoAppliable("MFG_ECO_APPLIED"));
        assertTrue(!MfgDevServices.ecoAppliable("MFG_ECO_CANCELLED"));
        assertTrue(!MfgDevServices.ecoAppliable(null));
    }

    @Test
    public void formatsDevelopmentProjectAndChangeOrderIds() {
        LocalDate day = LocalDate.of(2026, 10, 21);
        assertEquals("DEV-261021-01", MfgDevServices.formatDevProjectId(day, 1));
        assertEquals("DEV-261021-12", MfgDevServices.formatDevProjectId(day, 12));
        assertEquals("ECO-261021-01", MfgDevServices.formatEcoId(day, 1));
        assertEquals("ECO-261021-07", MfgDevServices.formatEcoId(day, 7));
    }

    @Test
    public void knowsTheStepsOfEachDevelopmentTrack() {
        assertEquals(5, MfgDevServices.stepsOfTrack(MfgConstants.TRACK_PCB).length);
        assertEquals(3, MfgDevServices.stepsOfTrack(MfgConstants.TRACK_FIRMWARE).length);
        assertEquals(7, MfgDevServices.stepsOfTrack(MfgConstants.TRACK_ENCLOSURE).length);
        assertEquals(0, MfgDevServices.stepsOfTrack("").length);
        assertEquals(0, MfgDevServices.stepsOfTrack(null).length);
        assertEquals(0, MfgDevServices.stepsOfTrack("MFG_TRACK_BOGUS").length);

        // the PCB track starts on the breadboard and ends on the hand soldered test
        assertEquals("MFG_PCB_BREADBOARD", MfgDevServices.stepsOfTrack(MfgConstants.TRACK_PCB)[0]);
        assertEquals("MFG_PCB_HAND_SOLDER", MfgDevServices.stepsOfTrack(MfgConstants.TRACK_PCB)[4]);
        // the enclosure track ends with the product assembly
        assertEquals("MFG_ENC_ASSEMBLY", MfgDevServices.stepsOfTrack(MfgConstants.TRACK_ENCLOSURE)[6]);

        assertTrue(MfgDevServices.isKnownTrack(MfgConstants.TRACK_ENCLOSURE));
        assertTrue(!MfgDevServices.isKnownTrack("MFG_TRACK_BOGUS"));

        assertTrue(MfgDevServices.isKnownStep(MfgConstants.TRACK_FIRMWARE, "MFG_FW_FLASH"));
        assertTrue(!MfgDevServices.isKnownStep(MfgConstants.TRACK_PCB, "MFG_FW_FLASH"));
        assertTrue(!MfgDevServices.isKnownStep("", "MFG_PCB_SCHEMATIC"));
    }

    @Test
    public void findsTheFirstOpenStep() {
        // nothing done yet, so the first step of the track is still open
        assertEquals("MFG_PCB_BREADBOARD",
                MfgDevServices.firstOpenStep(MfgConstants.TRACK_PCB, new java.util.ArrayList<String>()));

        java.util.List<String> done = java.util.Arrays.asList("MFG_PCB_BREADBOARD", "MFG_PCB_SCHEMATIC");
        assertEquals("MFG_PCB_LAYOUT", MfgDevServices.firstOpenStep(MfgConstants.TRACK_PCB, done));

        java.util.List<String> all = java.util.Arrays.asList(MfgConstants.PCB_STEPS);
        assertNull(MfgDevServices.firstOpenStep(MfgConstants.TRACK_PCB, all));

        // a track without steps has nothing to open
        assertNull(MfgDevServices.firstOpenStep("", done));
    }

    @Test
    public void countsSkippedStepsAsFinished() {
        assertTrue(MfgDevServices.stepFinished("MFG_STEP_DONE"));
        assertTrue(MfgDevServices.stepFinished("MFG_STEP_SKIPPED"));
        assertTrue(!MfgDevServices.stepFinished("MFG_STEP_DOING"));
        assertTrue(!MfgDevServices.stepFinished("MFG_STEP_TODO"));
        assertTrue(!MfgDevServices.stepFinished(null));

        assertTrue(MfgDevServices.isKnownStepStatus("MFG_STEP_DOING"));
        assertTrue(!MfgDevServices.isKnownStepStatus("DONE"));
    }

    @Test
    public void knowsTheAllowedPhotoTypes() {
        assertTrue(MfgAttachmentServices.isAllowedImageType("image/jpeg"));
        assertTrue(MfgAttachmentServices.isAllowedImageType("image/png"));
        assertTrue(MfgAttachmentServices.isAllowedImageType("IMAGE/GIF"));
        assertTrue(MfgAttachmentServices.isAllowedImageType("  image/png  "));
        assertTrue(!MfgAttachmentServices.isAllowedImageType("image/webp"));
        assertTrue(!MfgAttachmentServices.isAllowedImageType("application/pdf"));
        assertTrue(!MfgAttachmentServices.isAllowedImageType(""));
        assertTrue(!MfgAttachmentServices.isAllowedImageType(null));
    }

    @Test
    public void guessesThePhotoTypeFromTheFileName() {
        assertEquals("image/jpeg", MfgAttachmentServices.imageTypeOfFileName("front.JPG"));
        assertEquals("image/jpeg", MfgAttachmentServices.imageTypeOfFileName("C:\\photos\\back.jpeg"));
        assertEquals("image/png", MfgAttachmentServices.imageTypeOfFileName("board.png"));
        assertEquals("image/gif", MfgAttachmentServices.imageTypeOfFileName("anim.Gif"));
        assertEquals("application/pdf", MfgAttachmentServices.imageTypeOfFileName("report.pdf"));
        assertNull(MfgAttachmentServices.imageTypeOfFileName("notes.txt"));
        assertNull(MfgAttachmentServices.imageTypeOfFileName("noextension"));
        assertNull(MfgAttachmentServices.imageTypeOfFileName(null));
    }

    @Test
    public void allowsImagesAndPdfOnly() {
        assertTrue(MfgAttachmentServices.isAllowedAttachmentType("image/png"));
        assertTrue(MfgAttachmentServices.isAllowedAttachmentType("application/pdf"));
        assertTrue(MfgAttachmentServices.isAllowedAttachmentType("APPLICATION/PDF"));
        assertTrue(!MfgAttachmentServices.isAllowedAttachmentType("text/plain"));
        assertTrue(!MfgAttachmentServices.isAllowedAttachmentType("application/zip"));
        assertTrue(!MfgAttachmentServices.isAllowedAttachmentType(null));
        // the pdf is not an image, so it never gets a thumbnail
        assertTrue(!MfgAttachmentServices.isAllowedImageType("application/pdf"));
    }

    @Test
    public void knowsTheAttachmentOwnerTypes() {
        assertTrue(MfgAttachmentServices.isKnownAttachmentOwnerType("MFG_ATT_PRODUCT"));
        assertTrue(MfgAttachmentServices.isKnownAttachmentOwnerType("MFG_ATT_DEV_PROJECT"));
        assertTrue(MfgAttachmentServices.isKnownAttachmentOwnerType("MFG_ATT_WORK_ORDER"));
        assertTrue(MfgAttachmentServices.isKnownAttachmentOwnerType("MFG_ATT_DELIVERY"));
        assertTrue(MfgAttachmentServices.isKnownAttachmentOwnerType("MFG_ATT_CLAIM"));
        assertTrue(!MfgAttachmentServices.isKnownAttachmentOwnerType("MFG_ATT_BOGUS"));
        assertTrue(!MfgAttachmentServices.isKnownAttachmentOwnerType(null));
    }

    @Test
    public void cleansUploadedFileNames() {
        assertEquals("photo.png", MfgAttachmentServices.cleanFileName("C:\\Users\\me\\Pictures\\photo.png"));
        assertEquals("photo.png", MfgAttachmentServices.cleanFileName("/tmp/photo.png"));
        // characters outside letters, digits and dot dash underscore become underscores
        String cleaned = MfgAttachmentServices.cleanFileName("外壳 正面.jpg");
        assertTrue("expected only underscores and the extension, got " + cleaned, cleaned.matches("_+\\.jpg"));
        assertEquals("file", MfgAttachmentServices.cleanFileName(""));
        assertEquals("file", MfgAttachmentServices.cleanFileName(null));
        assertTrue(MfgAttachmentServices.cleanFileName("a".repeat(200) + ".png").length() <= 60);
    }

    @Test
    public void detectsTheBomFormat() {
        // Fritzing writes a header with a semicolon; everything else stays the component format
        assertTrue(MfgCsv.detectBomFormat("Label;Part Type;mn;mpn;part number;\nBT1;Supercapacitor;;;SE-5R5;\n")
                == MfgCsv.BomFormat.FRITZING);
        assertTrue(MfgCsv.detectBomFormat("\uFEFFAmount;Part Type;mn;mpn;part number;\n1;Resistor;;;;\n")
                == MfgCsv.BomFormat.FRITZING);
        assertTrue(MfgCsv.detectBomFormat("R0402-10K,10K 电阻,4,R1-R4,N,0.01\n")
                == MfgCsv.BomFormat.MFG);
        assertTrue(MfgCsv.detectBomFormat("") == MfgCsv.BomFormat.MFG);
        assertTrue(MfgCsv.detectBomFormat(null) == MfgCsv.BomFormat.MFG);
    }

    @Test
    public void parsesAFritzingBom() {
        String csv = "Label;Part Type;mn;mpn;part number;\n"
                + "BT1;Supercapacitor 1.5F 5.5V;;;SE-5R5-D155VYC3G;\n"
                + "J1;PIN HEADER 1x4;;;PinHeader-1x4;\n"
                + "J3;PIN HEADER 1x4;;;PinHeader-1x4;\n"
                + "R1;4.7kΩ Resistor;;;;\n"
                + "R2;4.7kΩ Resistor;;;;\n"
                + "\n"
                + "Amount;Part Type;mn;mpn;part number;\n"
                + "1;Supercapacitor 1.5F 5.5V;;;SE-5R5-D155VYC3G;\n"
                + "2;PIN HEADER 1x4;;;PinHeader-1x4;\n"
                + "2;4.7kΩ Resistor;;;;\n";
        List<String[]> rows = MfgCsv.parseFritzingBom(csv);

        assertEquals(3, rows.size());
        // the part number becomes the code, the amount block the quantity
        assertEquals("SE-5R5-D155VYC3G", rows.get(0)[0]);
        assertEquals("Supercapacitor 1.5F 5.5V", rows.get(0)[1]);
        assertEquals("1", rows.get(0)[2]);
        assertEquals("BT1", rows.get(0)[3]);
        assertEquals("N", rows.get(0)[4]);
        assertEquals("PINHEADER-1X4", rows.get(1)[0]);
        assertEquals("2", rows.get(1)[2]);
        assertEquals("J1,J3", rows.get(1)[3]);
        // no part number: the name is used as the code, upper cased
        assertEquals("4.7KΩ RESISTOR", rows.get(2)[0]);
        assertEquals("2", rows.get(2)[2]);
        assertEquals("R1,R2", rows.get(2)[3]);
    }

    @Test
    public void countsAFritzingBomWithoutTheAmountBlock() {
        // a schematic export without the amount block: one row per designator
        String csv = "Label;Part Type;mn;mpn;part number;\n"
                + "J1;PIN HEADER 1x4;;;PinHeader-1x4;\n"
                + "J3;PIN HEADER 1x4;;;PinHeader-1x4;\n";
        List<String[]> rows = MfgCsv.parseFritzingBom(csv);

        assertEquals(1, rows.size());
        assertEquals("2", rows.get(0)[2]);
        assertEquals("J1,J3", rows.get(0)[3]);
    }

    @Test
    public void parsesTheBomAfterDetectingTheFormat() {
        String fritzing = "Label;Part Type;mn;mpn;part number;\nU1;CH32V203F8U6;;;CH32V203F8U6;\n";
        List<String[]> detected = MfgCsv.parseBom(fritzing, null);
        assertEquals(1, detected.size());
        assertEquals("CH32V203F8U6", detected.get(0)[0]);

        // the format can be forced, which also works for text that looks like neither
        List<String[]> forced = MfgCsv.parseBom("U9;SoC;;;U9-CHIP;\n", MfgCsv.BomFormat.FRITZING);
        assertEquals(1, forced.size());
        assertEquals("U9-CHIP", forced.get(0)[0]);
    }
}
