Option Explicit

Const BITS_TO_A_BYTE = 8
Const BYTES_TO_A_WORD = 4
Const BITS_TO_A_WORD = 32

Dim PROJECT_FOLDER, VERSION, FOLDER_TO_ZIP, TARGET_ZIP, ADDITIONAL_FILES, m_l2Power, m_lOnBits, zipHash

Initializer
zipHash = Hash(UserInput(""))

PROJECT_FOLDER = Expand("%USERPROFILE%\Documents\GitHub\qz-print\")
VERSION = GetVersion(PROJECT_FOLDER & "qz-print\src\qz\PrintApplet.java", "String VERSION")
FOLDER_TO_ZIP = PROJECT_FOLDER & "qz-print\dist"
TARGET_ZIP = Expand("%userprofile%\Desktop\qz-print_" & VERSION & zipHash & ".zip")
ADDITIONAL_FILES = Array( _
	PROJECT_FOLDER & "README.TXT", _
	PROJECT_FOLDER & "CHANGELOG.TXT", _
	PROJECT_FOLDER & "LICENSE.TXT", _
	PROJECT_FOLDER & "..\SOFTWARE AGREEMENT.TXT" _
)

MakeZip FOLDER_TO_ZIP, TARGET_ZIP

WScript.Echo "Finished creating bundle:" & vbNewLine & vbTab & TARGET_ZIP

Sub MakeZip (sFolder, zipFile)
    With CreateObject("Scripting.FileSystemObject")
        zipFile = .GetAbsolutePathName(zipFile)
        sFolder = .GetAbsolutePathName(sFolder)

        With .CreateTextFile(zipFile, True)
            .Write Chr(80) & Chr(75) & Chr(5) & Chr(6) & String(18, chr(0))
        End With
    End With


    With CreateObject("Shell.Application")
        .NameSpace(zipFile).CopyHere .NameSpace(sFolder)

        Do Until .NameSpace(zipFile).Items.Count = _
                 1
            WScript.Sleep 1000 
        Loop
		
		Dim extraFile, counter
		counter = 0
		For Each extraFile in ADDITIONAL_FILES
			With CreateObject("Scripting.FileSystemObject")
				extraFile = .GetAbsolutePathName(extraFile)
			End With
			.NameSpace(zipFile).CopyHere extraFile
			counter = counter + 1
			Do Until .NameSpace(zipFile).Items.Count = counter + 1
				WScript.Sleep 1000
			Loop
		Next
		
    End With

End Sub

Function UserInput(myPrompt)
    If UCase( Right( WScript.FullName, 12 ) ) = "\CSCRIPT.EXE" Then
        WScript.StdOut.Write myPrompt & " "
        UserInput = WScript.StdIn.ReadLine
    Else
        UserInput = InputBox( myPrompt )
    End If
End Function

Function GetVersion(javaSource, lineContains)
	Dim oFSO, listFile, fName
	Set oFSO = CreateObject("Scripting.FileSystemObject")
	Set listFile = oFSO.OpenTextFile(javaSource)
	Do Until listFile.AtEndOfStream
		fName = listFile.ReadLine
		If InStr(fName, lineContains) > 0 Then
			GetVersion = Trim(Split(fName, CHR(34))(1))
			Exit Function
		Else
			GetVersion = "x.x.x"
		End If
	Loop
End Function

Function Expand(path) 
	Dim oWSH
	Set oWSH = WScript.CreateObject("WScript.Shell")
	Expand = oWSH.ExpandEnvironmentStrings(path)
	Set oWSH = Nothing
End Function


Sub Initializer 
	If Not IsArray(m_lOnBits) Then
		m_lOnBits = Array( _
			CLng(1),CLng(3),CLng(7),CLng(15),CLng(31),CLng(63),CLng(127), _
			CLng(255),CLng(511),CLng(1023),CLng(2047),CLng(4095),CLng(8191), _
			CLng(16383),CLng(32767),CLng(65535),CLng(131071),CLng(262143), _
			CLng(524287),CLng(1048575),CLng(2097151),CLng(4194303),CLng(8388607), _
			CLng(16777215),CLng(33554431),CLng(67108863),CLng(134217727), _
			CLng(268435455),CLng(536870911),CLng(1073741823),CLng(2147483647) _
		)

		m_l2Power = Array( _
			CLng(1),CLng(2),CLng(4),CLng(8),CLng(16),CLng(32),CLng(64), _
			CLng(128),CLng(256),CLng(512),CLng(1024),CLng(2048),CLng(4096), _
			CLng(8192),CLng(16384),CLng(32768),CLng(65536),CLng(131072), _
			CLng(262144),CLng(524288),CLng(1048576),CLng(2097152),CLng(4194304), _
			CLng(8388608),CLng(16777216),CLng(33554432),CLng(67108864), _
			CLng(134217728),CLng(268435456),CLng(536870912),CLng(1073741824) _
		)
	End If
End Sub

Function Hash(data)
	If (data = "") Then
		Hash = ""
	Else
		Hash="_" & Right(MD5(data), 4)
	End If
End Function
	

Function LShift(lValue, iShiftBits)
	If iShiftBits = 0 Then
		LShift = lValue
		Exit Function
	ElseIf iShiftBits = 31 Then
		If lValue And 1 Then
			LShift = &H80000000
		Else
			[EMBED]
			LShift = 0
		End If
		Exit Function
	ElseIf iShiftBits < 0 Or iShiftBits > 31 Then
		Err.Raise 6
	End If

	If (lValue And m_l2Power(31 - iShiftBits)) Then
		LShift = ((lValue And m_lOnBits(31 - (iShiftBits + 1))) * m_l2Power(iShiftBits)) Or &H80000000
	Else
		LShift = ((lValue And m_lOnBits(31 - iShiftBits)) * m_l2Power(iShiftBits))
	End If
End Function

Function RShift(lValue, iShiftBits)
	If iShiftBits = 0 Then
		RShift = lValue
		Exit Function
	ElseIf iShiftBits = 31 Then
		If lValue And &H80000000 Then
			RShift = 1
		Else
			RShift = 0
		End If
		Exit Function
	ElseIf iShiftBits < 0 Or iShiftBits > 31 Then
		Err.Raise 6
	End If
	
	RShift = (lValue And &H7FFFFFFE) \ m_l2Power(iShiftBits)
	
	If (lValue And &H80000000) Then
		RShift = (RShift Or (&H40000000 \ m_l2Power(iShiftBits - 1)))
	End If
End Function

Function RotateLeft(lValue, iShiftBits)
	RotateLeft = LShift(lValue, iShiftBits) Or RShift(lValue, (32 - iShiftBits))
End Function

Function AddUnsigned(lX, lY)
	Dim lX4
	Dim lY4
	Dim lX8
	Dim lY8
	Dim lResult
	
	lX8 = lX And &H80000000
	lY8 = lY And &H80000000
	lX4 = lX And &H40000000
	lY4 = lY And &H40000000
	
	lResult = (lX And &H3FFFFFFF) + (lY And &H3FFFFFFF)
	
	If lX4 And lY4 Then
		lResult = lResult Xor &H80000000 Xor lX8 Xor lY8
	ElseIf lX4 Or lY4 Then
		If lResult And &H40000000 Then
			lResult = lResult Xor &HC0000000 Xor lX8 Xor lY8
		Else
			lResult = lResult Xor &H40000000 Xor lX8 Xor lY8
		End If
	Else
		lResult = lResult Xor lX8 Xor lY8
	End If
	
	AddUnsigned = lResult
End Function

Function F(x, y, z)
	F = (x And y) Or ((Not x) And z)
End Function

Function G(x, y, z)
	G = (x And z) Or (y And (Not z))
End Function

Function H(x, y, z)
	H = (x Xor y Xor z)
End Function

Function I(x, y, z)
	I = (y Xor (x Or (Not z)))
End Function

Sub FF(a, b, c, d, x, s, ac)
	a = AddUnsigned(a, AddUnsigned(AddUnsigned(F(b, c, d), x), ac))
	a = RotateLeft(a, s)
	a = AddUnsigned(a, b)
End Sub

Sub GG(a, b, c, d, x, s, ac)
	a = AddUnsigned(a, AddUnsigned(AddUnsigned(G(b, c, d), x), ac))
	a = RotateLeft(a, s)
	a = AddUnsigned(a, b)
End Sub

Sub HH(a, b, c, d, x, s, ac)
	a = AddUnsigned(a, AddUnsigned(AddUnsigned(H(b, c, d), x), ac))
	a = RotateLeft(a, s)
	a = AddUnsigned(a, b)
End Sub

Sub II(a, b, c, d, x, s, ac)
	a = AddUnsigned(a, AddUnsigned(AddUnsigned(I(b, c, d), x), ac))
	a = RotateLeft(a, s)
	a = AddUnsigned(a, b)
End Sub

Function ConvertToWordArray(sMessage)
	Dim lMessageLength
	Dim lNumberOfWords
	Dim lWordArray()
	Dim lBytePosition
	Dim lByteCount
	Dim lWordCount
	
	Const MODULUS_BITS = 512
	Const CONGRUENT_BITS = 448
	
	lMessageLength = Len(sMessage)
	
	lNumberOfWords = (((lMessageLength + ((MODULUS_BITS - CONGRUENT_BITS) \ BITS_TO_A_BYTE)) \ (MODULUS_BITS \ BITS_TO_A_BYTE)) + 1) * (MODULUS_BITS \ BITS_TO_A_WORD)
	ReDim lWordArray(lNumberOfWords - 1)
	
	lBytePosition = 0
	lByteCount = 0
	Do Until lByteCount >= lMessageLength
		lWordCount = lByteCount \ BYTES_TO_A_WORD
		lBytePosition = (lByteCount Mod BYTES_TO_A_WORD) * BITS_TO_A_BYTE
		lWordArray(lWordCount) = lWordArray(lWordCount) Or LShift(Asc(Mid(sMessage, lByteCount + 1, 1)), lBytePosition)
		lByteCount = lByteCount + 1
	Loop
	
	lWordCount = lByteCount \ BYTES_TO_A_WORD
	lBytePosition = (lByteCount Mod BYTES_TO_A_WORD) * BITS_TO_A_BYTE
	
	lWordArray(lWordCount) = lWordArray(lWordCount) Or LShift(&H80, lBytePosition)
	
	lWordArray(lNumberOfWords - 2) = LShift(lMessageLength, 3)
	lWordArray(lNumberOfWords - 1) = RShift(lMessageLength, 29)
	
	ConvertToWordArray = lWordArray
End Function

Function WordToHex(lValue)
	Dim lByte
	Dim lCount
	
	For lCount = 0 To 3
		lByte = RShift(lValue, lCount * BITS_TO_A_BYTE) And m_lOnBits(BITS_TO_A_BYTE - 1)
		WordToHex = WordToHex & Right("0" & Hex(lByte), 2)
	Next
End Function

Public Function MD5(sMessage)
	Dim x
	Dim k
	Dim AA
	Dim BB
	Dim CC
	Dim DD
	Dim a
	Dim b
	Dim c
	Dim d
	
	Const S11 = 7
	Const S12 = 12
	Const S13 = 17
	Const S14 = 22
	Const S21 = 5
	Const S22 = 9
	Const S23 = 14
	Const S24 = 20
	Const S31 = 4
	Const S32 = 11
	Const S33 = 16
	Const S34 = 23
	Const S41 = 6
	Const S42 = 10
	Const S43 = 15
	Const S44 = 21
	
	x = ConvertToWordArray(sMessage)
	
	a = &H67452301
	b = &HEFCDAB89
	c = &H98BADCFE
	d = &H10325476
	
	For k = 0 To UBound(x) Step 16
		AA = a
		BB = b
		CC = c
		DD = d
		
		FF a, b, c, d, x(k + 0), S11, &HD76AA478
		FF d, a, b, c, x(k + 1), S12, &HE8C7B756
		FF c, d, a, b, x(k + 2), S13, &H242070DB
		FF b, c, d, a, x(k + 3), S14, &HC1BDCEEE
		FF a, b, c, d, x(k + 4), S11, &HF57C0FAF
		FF d, a, b, c, x(k + 5), S12, &H4787C62A
		FF c, d, a, b, x(k + 6), S13, &HA8304613
		FF b, c, d, a, x(k + 7), S14, &HFD469501
		FF a, b, c, d, x(k + 8), S11, &H698098D8
		FF d, a, b, c, x(k + 9), S12, &H8B44F7AF
		FF c, d, a, b, x(k + 10), S13, &HFFFF5BB1
		FF b, c, d, a, x(k + 11), S14, &H895CD7BE
		FF a, b, c, d, x(k + 12), S11, &H6B901122
		FF d, a, b, c, x(k + 13), S12, &HFD987193
		FF c, d, a, b, x(k + 14), S13, &HA679438E
		FF b, c, d, a, x(k + 15), S14, &H49B40821
		
		GG a, b, c, d, x(k + 1), S21, &HF61E2562
		GG d, a, b, c, x(k + 6), S22, &HC040B340
		GG c, d, a, b, x(k + 11), S23, &H265E5A51
		GG b, c, d, a, x(k + 0), S24, &HE9B6C7AA
		GG a, b, c, d, x(k + 5), S21, &HD62F105D
		GG d, a, b, c, x(k + 10), S22, &H2441453
		GG c, d, a, b, x(k + 15), S23, &HD8A1E681
		GG b, c, d, a, x(k + 4), S24, &HE7D3FBC8
		GG a, b, c, d, x(k + 9), S21, &H21E1CDE6
		GG d, a, b, c, x(k + 14), S22, &HC33707D6
		GG c, d, a, b, x(k + 3), S23, &HF4D50D87
		GG b, c, d, a, x(k + 8), S24, &H455A14ED
		GG a, b, c, d, x(k + 13), S21, &HA9E3E905
		GG d, a, b, c, x(k + 2), S22, &HFCEFA3F8
		GG c, d, a, b, x(k + 7), S23, &H676F02D9
		GG b, c, d, a, x(k + 12), S24, &H8D2A4C8A
		
		HH a, b, c, d, x(k + 5), S31, &HFFFA3942
		HH d, a, b, c, x(k + 8), S32, &H8771F681
		HH c, d, a, b, x(k + 11), S33, &H6D9D6122
		HH b, c, d, a, x(k + 14), S34, &HFDE5380C
		HH a, b, c, d, x(k + 1), S31, &HA4BEEA44
		HH d, a, b, c, x(k + 4), S32, &H4BDECFA9
		HH c, d, a, b, x(k + 7), S33, &HF6BB4B60
		HH b, c, d, a, x(k + 10), S34, &HBEBFBC70
		HH a, b, c, d, x(k + 13), S31, &H289B7EC6
		HH d, a, b, c, x(k + 0), S32, &HEAA127FA
		HH c, d, a, b, x(k + 3), S33, &HD4EF3085
		HH b, c, d, a, x(k + 6), S34, &H4881D05
		HH a, b, c, d, x(k + 9), S31, &HD9D4D039
		HH d, a, b, c, x(k + 12), S32, &HE6DB99E5
		HH c, d, a, b, x(k + 15), S33, &H1FA27CF8
		HH b, c, d, a, x(k + 2), S34, &HC4AC5665
		
		II a, b, c, d, x(k + 0), S41, &HF4292244
		II d, a, b, c, x(k + 7), S42, &H432AFF97
		II c, d, a, b, x(k + 14), S43, &HAB9423A7
		II b, c, d, a, x(k + 5), S44, &HFC93A039
		II a, b, c, d, x(k + 12), S41, &H655B59C3
		II d, a, b, c, x(k + 3), S42, &H8F0CCC92
		II c, d, a, b, x(k + 10), S43, &HFFEFF47D
		II b, c, d, a, x(k + 1), S44, &H85845DD1
		II a, b, c, d, x(k + 8), S41, &H6FA87E4F
		II d, a, b, c, x(k + 15), S42, &HFE2CE6E0
		II c, d, a, b, x(k + 6), S43, &HA3014314
		II b, c, d, a, x(k + 13), S44, &H4E0811A1
		II a, b, c, d, x(k + 4), S41, &HF7537E82
		II d, a, b, c, x(k + 11), S42, &HBD3AF235
		II c, d, a, b, x(k + 2), S43, &H2AD7D2BB
		II b, c, d, a, x(k + 9), S44, &HEB86D391
		
		a = AddUnsigned(a, AA)
		b = AddUnsigned(b, BB)
		c = AddUnsigned(c, CC)
		d = AddUnsigned(d, DD)
	Next
	
	MD5 = LCase(WordToHex(a) & WordToHex(b) & WordToHex(c) & WordToHex(d))
End Function
