package org.swmaestro.repl.gifthub.vouchers.service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.swmaestro.repl.gifthub.auth.service.MemberService;
import org.swmaestro.repl.gifthub.exception.BusinessException;
import org.swmaestro.repl.gifthub.exception.ErrorCode;
import org.swmaestro.repl.gifthub.util.DateConverter;
import org.swmaestro.repl.gifthub.vouchers.dto.VoucherReadResponseDto;
import org.swmaestro.repl.gifthub.vouchers.dto.VoucherSaveRequestDto;
import org.swmaestro.repl.gifthub.vouchers.dto.VoucherSaveResponseDto;
import org.swmaestro.repl.gifthub.vouchers.dto.VoucherUpdateRequestDto;
import org.swmaestro.repl.gifthub.vouchers.dto.VoucherUseRequestDto;
import org.swmaestro.repl.gifthub.vouchers.dto.VoucherUseResponseDto;
import org.swmaestro.repl.gifthub.vouchers.entity.Voucher;
import org.swmaestro.repl.gifthub.vouchers.entity.VoucherUsageHistory;
import org.swmaestro.repl.gifthub.vouchers.repository.VoucherRepository;
import org.swmaestro.repl.gifthub.vouchers.repository.VoucherUsageHistoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VoucherService {
	@Value("${cloud.aws.s3.voucher-dir-name}")
	private String voucherDirName;
	private final StorageService storageService;
	private final BrandService brandService;
	private final ProductService productService;
	private final VoucherRepository voucherRepository;
	private final MemberService memberService;
	private final VoucherUsageHistoryRepository voucherUsageHistoryRepository;

	/*
		기프티콘 저장 메서드
	 */
	public VoucherSaveResponseDto save(String username, VoucherSaveRequestDto voucherSaveRequestDto) throws
			IOException {
		Voucher voucher = Voucher.builder()
				.brand(brandService.read(voucherSaveRequestDto.getBrandName()))
				.product(productService.read(voucherSaveRequestDto.getProductName()))
				.barcode(voucherSaveRequestDto.getBarcode())
				.expiresAt(DateConverter.stringToLocalDate(voucherSaveRequestDto.getExpiresAt()))
				.imageUrl(storageService.getBucketAddress(voucherDirName) + voucherSaveRequestDto.getImageUrl())
				.member(memberService.read(username))
				.build();

		return VoucherSaveResponseDto.builder()
				.id(voucherRepository.save(voucher).getId())
				.build();
	}

	/*
	기프티콘 상세 조회 메서드
	 */
	public VoucherReadResponseDto read(Long id, String username) {
		Optional<Voucher> voucher = voucherRepository.findById(id);
		List<Voucher> vouchers = voucherRepository.findAllByMemberUsername(username);

		if (voucher.isEmpty()) {
			throw new BusinessException("존재하지 않는 상품권 입니다.", ErrorCode.NOT_FOUND_RESOURCE);
		}
		if (!vouchers.contains(voucher.get())) {
			throw new BusinessException("상품권을 조회할 권한이 없습니다.", ErrorCode.ACCESS_DENIED);
		}

		VoucherReadResponseDto voucherReadResponseDto = mapToDto(voucher.get());
		return voucherReadResponseDto;
	}

	/*
	사용자 별 기프티콘 목록 조회 메서드
	 */
	public List<Long> list(String username) {
		List<Voucher> vouchers = voucherRepository.findAllByMemberUsername(username);
		if (vouchers == null) {
			throw new BusinessException("존재하지 않는 사용자 입니다.", ErrorCode.NOT_FOUND_RESOURCE);
		}
		List<Long> voucherIdList = new ArrayList<>();
		for (Voucher voucher : vouchers) {
			voucherIdList.add(voucher.getId());
		}
		return voucherIdList;
	}

	/*
	기프티콘 정보 수정 메서드
	 */
	public VoucherSaveResponseDto update(Long voucherId, VoucherUpdateRequestDto voucherUpdateRequestDto) {
		Voucher voucher = voucherRepository.findById(voucherId)
				.orElseThrow(() -> new BusinessException("존재하지 않는 상품권 입니다.", ErrorCode.NOT_FOUND_RESOURCE));

		voucher.setBarcode(
				voucherUpdateRequestDto.getBarcode() == null ? voucher.getBarcode() :
						voucherUpdateRequestDto.getBarcode());
		voucher.setBrand(voucherUpdateRequestDto.getBrandName() == null ? voucher.getBrand() :
				brandService.read(voucherUpdateRequestDto.getBrandName()));
		voucher.setProduct(voucherUpdateRequestDto.getProductName() == null ? voucher.getProduct() :
				productService.read(voucherUpdateRequestDto.getProductName()));
		voucher.setExpiresAt(voucherUpdateRequestDto.getExpiresAt() == null ? voucher.getExpiresAt() :
				DateConverter.stringToLocalDate(voucherUpdateRequestDto.getExpiresAt()));

		return VoucherSaveResponseDto.builder()
				.id(voucherId)
				.build();
	}

	/*
	기프티콘 사용 등록 메서드
	 */
	public VoucherUseResponseDto use(String username, Long voucherId, VoucherUseRequestDto voucherUseRequestDto) {
		Optional<Voucher> voucher = voucherRepository.findById(voucherId);
		List<Voucher> vouchers = voucherRepository.findAllByMemberUsername(username);
		List<VoucherUsageHistory> voucherUsageHistories = voucherUsageHistoryRepository.findAllByVoucherId(voucherId);

		if (voucher.isEmpty()) {
			throw new BusinessException("존재하지 않는 상품권 입니다.", ErrorCode.NOT_FOUND_RESOURCE);
		}
		if (!vouchers.contains(voucher.get())) {
			throw new BusinessException("상품권을 사용할 권한이 없습니다.", ErrorCode.ACCESS_DENIED);
		}
		int totalUsageAmount = voucherUsageHistories.stream()
				.mapToInt(VoucherUsageHistory::getAmount)
				.sum();

		totalUsageAmount = Math.max(totalUsageAmount, 0);

		if (totalUsageAmount == voucher.get().getBalance()) {
			throw new BusinessException("이미 사용된 상품권 입니다.", ErrorCode.NOT_FOUND_RESOURCE);
		}

		int remainingBalance = voucher.get().getBalance() - totalUsageAmount;
		int requestedAmount = voucherUseRequestDto.getAmount();

		if (requestedAmount > remainingBalance) {
			throw new BusinessException("잔액이 부족합니다.", ErrorCode.EXIST_RESOURCE);
		}

		if (voucher.get().getExpiresAt().isBefore(LocalDate.now())) {
			throw new BusinessException("유효기간이 만료된 상품권 입니다.", ErrorCode.EXIST_RESOURCE);
		}

		VoucherUsageHistory voucherUsageHistory = VoucherUsageHistory.builder()
				.member(memberService.read(username))
				.voucher(voucher.get())
				.amount(voucherUseRequestDto.getAmount())
				.place(voucherUseRequestDto.getPlace())
				.createdAt(LocalDateTime.now())
				.build();

		voucherUsageHistoryRepository.save(voucherUsageHistory);

		return VoucherUseResponseDto.builder()
				.usageId(voucherUsageHistory.getId())
				.voucherId(voucherId)
				.balance(remainingBalance - requestedAmount)
				.build();
	}

	/*
	Entity를 Dto로 변환하는 메서드
	 */
	public VoucherReadResponseDto mapToDto(Voucher voucher) {
		VoucherReadResponseDto voucherReadResponseDto = VoucherReadResponseDto.builder()
				.id(voucher.getId())
				.productId(voucher.getProduct().getId())
				.barcode(voucher.getBarcode())
				.expiresAt(voucher.getExpiresAt().toString())
				.build();
		return voucherReadResponseDto;
	}
}
