package vn.hutech.fooddelivery.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.hutech.fooddelivery.common.NotFoundException;
import vn.hutech.fooddelivery.user.dto.AddressRequest;
import vn.hutech.fooddelivery.user.dto.AddressResponse;

import java.util.List;

/**
 * So dia chi giao hang. Quy tac:
 *   - Co it nhat 1 dia chi thi luon co dung 1 dia chi mac dinh.
 *   - Dia chi dau tien tu thanh mac dinh; xoa dia chi mac dinh thi dia chi moi nhat con lai thanh mac dinh.
 *   - userId lay tu token, dia chi cua nguoi khac thi tra 404 (khong lo la co ton tai).
 */
@Service
public class AddressService {

    private final AddressRepository addressRepository;

    public AddressService(AddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> list(long userId) {
        return addressRepository.findByUser(userId);
    }

    @Transactional
    public AddressResponse create(long userId, AddressRequest req) {
        // Dia chi dau tien luon la mac dinh, du client gui isDefault = false
        boolean makeDefault = req.isDefault() || addressRepository.countByUser(userId) == 0;
        if (makeDefault) {
            addressRepository.clearDefault(userId);
        }
        long id = addressRepository.insert(userId, cleanLabel(req.label()), req.receiverName().trim(),
                req.receiverPhone().trim(), req.addressLine().trim(), req.lat(), req.lng(), makeDefault);
        return find(id, userId);
    }

    /**
     * Sua noi dung dia chi. isDefault = true thi dat lam mac dinh;
     * isDefault = false KHONG bo mac dinh (muon doi mac dinh thi dat dia chi khac lam mac dinh).
     */
    @Transactional
    public AddressResponse update(long userId, long id, AddressRequest req) {
        find(id, userId);
        addressRepository.update(id, userId, cleanLabel(req.label()), req.receiverName().trim(),
                req.receiverPhone().trim(), req.addressLine().trim(), req.lat(), req.lng());
        if (req.isDefault()) {
            makeDefault(userId, id);
        }
        return find(id, userId);
    }

    @Transactional
    public AddressResponse setDefault(long userId, long id) {
        find(id, userId);
        makeDefault(userId, id);
        return find(id, userId);
    }

    @Transactional
    public void delete(long userId, long id) {
        AddressResponse address = find(id, userId);
        addressRepository.delete(id, userId);
        // Vua xoa dia chi mac dinh thi chon dia chi moi nhat con lai lam mac dinh
        if (address.isDefault()) {
            addressRepository.findNewestId(userId).ifPresent(newestId -> addressRepository.markDefault(newestId, userId));
        }
    }

    /** Bo mac dinh het roi danh dau 1 dia chi, trong cung transaction nen khong co luc nao co 2 mac dinh. */
    private void makeDefault(long userId, long id) {
        addressRepository.clearDefault(userId);
        addressRepository.markDefault(id, userId);
    }

    private AddressResponse find(long id, long userId) {
        return addressRepository.findByIdAndUser(id, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy địa chỉ"));
    }

    /** Ten goi nho de trong -> null. */
    private static String cleanLabel(String label) {
        return label == null || label.isBlank() ? null : label.trim();
    }
}
