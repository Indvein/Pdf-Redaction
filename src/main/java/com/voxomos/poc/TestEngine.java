package com.voxomos.poc;

import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;

import java.io.IOException;
import java.util.List;

public class TestEngine extends PDFStreamEngine {
    @Override
    protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
        super.processOperator(operator, operands);
    }
}
